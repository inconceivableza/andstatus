/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.note

import android.content.ContentValues
import android.net.Uri
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.actor.MentionedActorsLoader
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.AttachedImageFiles
import org.andstatus.app.data.AttachedMediaFile
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.DownloadType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.graphics.CacheName
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

class NoteEditorData private constructor(val ma: MyAccount?, activity: AActivity) : IsEmpty {
    val activity: AActivity?
    private var attachedImageFiles: AttachedImageFiles? = AttachedImageFiles.Companion.EMPTY
    private var replyToConversationParticipants = false
    private var replyToMentionedActors = false
    val myContext: MyContext?
    var timeline: Timeline? = Timeline.Companion.EMPTY

    constructor(myAccount: MyAccount, noteId: Long, initialize: Boolean,
                inReplyToNoteId: Long, andLoad: Boolean) : this(myAccount, toActivity(myAccount, noteId, andLoad)) {
        if (andLoad) {
            load(inReplyToNoteId)
        }
        if (initialize) {
            activity.initializePublicAndFollowers()
        }
    }

    private fun load(inReplyToNoteIdIn: Long) {
        val note = activity.getNote()
        val noteId = note.noteId
        note.name = MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId)
        note.summary = MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, noteId)
        note.isSensitive = MyQuery.isSensitive(noteId)
        note.setContentStored(MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId))
        note.setAudience(Audience.Companion.load(activity.accountActor.origin, noteId, Optional.empty()))
        val inReplyToNoteId = if (inReplyToNoteIdIn == 0L) MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, noteId) else inReplyToNoteIdIn
        if (inReplyToNoteId != 0L) {
            var inReplyToActorId = MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_ACTOR_ID, noteId)
            if (inReplyToActorId == 0L) {
                inReplyToActorId = MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, inReplyToNoteId)
            }
            val inReplyTo: AActivity = AActivity.Companion.newPartialNote(getMyAccount().getActor(),
                    Actor.Companion.load(myContext, inReplyToActorId),
                    MyQuery.idToOid(myContext, OidEnum.NOTE_OID, inReplyToNoteId, 0),
                    RelativeTime.DATETIME_MILLIS_NEVER, DownloadStatus.UNKNOWN)
            val inReplyToNote = inReplyTo.note
            inReplyToNote.noteId = inReplyToNoteId
            inReplyToNote.name = MyQuery.noteIdToStringColumnValue(NoteTable.NAME, inReplyToNoteId)
            inReplyToNote.summary = MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, inReplyToNoteId)
            inReplyToNote.audience().visibility = Visibility.Companion.fromNoteId(inReplyToNoteId)
            inReplyToNote.isSensitive = MyQuery.isSensitive(inReplyToNoteId)
            inReplyToNote.setContentStored(MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, inReplyToNoteId))
            note.setInReplyTo(inReplyTo)
        }
        attachedImageFiles = AttachedImageFiles.Companion.load(myContext, noteId)
        attachedImageFiles.list.forEach(Consumer { imageFile: AttachedMediaFile? -> imageFile.preloadImageAsync(CacheName.ATTACHED_IMAGE) })
        activity.setNote(note.withAttachments(Attachments.Companion.load(myContext, noteId)))
        MyLog.v(TAG) { "Loaded $this" }
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + ma.hashCode()
        result = prime * result + attachedImageFiles.hashCode()
        result = prime * result + activity.hashCode()
        return result
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val other = o as NoteEditorData?
        if (ma != other.ma) return false
        return if (attachedImageFiles != other.attachedImageFiles) false else activity == other.activity
    }

    override fun toString(): String {
        val builder: MyStringBuilder = MyStringBuilder.Companion.of(activity.toString())
        if (attachedImageFiles.nonEmpty()) {
            builder.withComma(attachedImageFiles.toString())
        }
        if (replyToConversationParticipants) {
            builder.withComma("ReplyAll")
        }
        builder.withComma("ma", ma.getAccountName())
        return MyStringBuilder.Companion.formatKeyValue(this, builder)
    }

    fun toTestSummary(): String? {
        val values = ContentValues()
        values.put(ActorTable.WEBFINGER_ID, activity.getActor().webFingerId)
        values.put(NoteTable.NAME, activity.getNote().name)
        values.put(NoteTable.SUMMARY, activity.getNote().summary)
        values.put(NoteTable.SENSITIVE, activity.getNote().isSensitive)
        values.put(NoteTable.CONTENT, activity.getNote().content)
        if (attachedImageFiles.nonEmpty()) {
            values.put(DownloadType.ATTACHMENT.name, attachedImageFiles.list.toString())
        }
        if (replyToConversationParticipants) {
            values.put("Reply", "all")
        }
        val inReplyTo = activity.getNote().inReplyTo
        if (inReplyTo.nonEmpty()) {
            val name = inReplyTo.note.name
            val summary = inReplyTo.note.summary
            values.put("InReplyTo", (if (!name.isNullOrEmpty()) name + MyStringBuilder.Companion.COMMA else "") +
                    (if (!summary.isNullOrEmpty()) summary + MyStringBuilder.Companion.COMMA else "") +
                    inReplyTo.note.content)
        }
        values.put("audience", activity.getNote().audience().toAudienceString(inReplyTo.author))
        values.put("ma", ma.getAccountName())
        return values.toString()
    }

    fun copy(): NoteEditorData? {
        return if (isValid()) {
            val data = NoteEditorData(ma, activity)
            data.attachedImageFiles = attachedImageFiles
            data.replyToConversationParticipants = replyToConversationParticipants
            data
        } else {
            EMPTY
        }
    }

    fun addAttachment(uri: Uri?, mediaType: Optional<String?>?) {
        activity.addAttachment(
                Attachment.Companion.fromUriAndMimeType(uri, mediaType.orElse("")),
                ma.getOrigin().originType.maxAttachmentsToSend
        )
    }

    fun save() {
        recreateKnownAudience(activity)
        DataUpdater(getMyAccount()).onActivity(activity)
        // TODO: Delete previous draft activities of this note
    }

    fun getMyAccount(): MyAccount? {
        return ma
    }

    override fun isEmpty(): Boolean {
        return activity.getNote().isEmpty
    }

    fun isValid(): Boolean {
        return this !== EMPTY && ma.isValid()
    }

    fun mayBeEdited(): Boolean {
        return Note.Companion.mayBeEdited(ma.getOrigin().originType, activity.getNote().status)
    }

    fun setContent(content: String?, mediaType: TextMediaType?): NoteEditorData? {
        activity.getNote().setContent(content, mediaType)
        return this
    }

    fun getContent(): String {
        return activity.getNote().content
    }

    fun getAttachedImageFiles(): AttachedImageFiles {
        return attachedImageFiles
    }

    fun setNoteId(noteId: Long): NoteEditorData? {
        activity.getNote().noteId = noteId
        return this
    }

    fun getNoteId(): Long {
        return activity.getNote().noteId
    }

    fun setReplyToConversationParticipants(replyToConversationParticipants: Boolean): NoteEditorData? {
        this.replyToConversationParticipants = replyToConversationParticipants
        return this
    }

    fun setReplyToMentionedActors(replyToMentionedUsers: Boolean): NoteEditorData? {
        replyToMentionedActors = replyToMentionedUsers
        return this
    }

    fun addMentionsToText(): NoteEditorData? {
        if (ma.isValid() && getInReplyToNoteId() != 0L) {
            if (replyToConversationParticipants) {
                addConversationParticipantsBeforeText()
            } else if (replyToMentionedActors) {
                addMentionedActorsBeforeText()
            } else {
                addActorsBeforeText(ArrayList())
            }
        }
        return this
    }

    private fun addConversationParticipantsBeforeText() {
        val loader = ConversationLoaderFactory().getLoader(
                ConversationViewItem.Companion.EMPTY,  MyContextHolder.myContextHolder.getNow(), ma.getOrigin(), getInReplyToNoteId(), false)
        loader.load { progress: String? -> }
        addActorsBeforeText(loader.list.stream()
                .filter { obj: ConversationViewItem? -> obj.isActorAConversationParticipant() }
                .map { o: ConversationViewItem? -> o.author.actor }.collect(Collectors.toList()))
    }

    private fun addMentionedActorsBeforeText() {
        val loader = MentionedActorsLoader(myContext, ma.getOrigin(),
                getInReplyToNoteId())
        loader.load(null)
        addActorsBeforeText(loader.list.stream().map { obj: ActorViewItem? -> obj.getActor() }.collect(Collectors.toList()))
    }

    fun addActorsBeforeText(toMention: MutableList<Actor?>?): NoteEditorData? {
        if (getInReplyToNoteId() != 0L) {
            toMention.add(0, Actor.Companion.load(myContext, MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, getInReplyToNoteId())))
        }
        val mentionedNames: MutableList<String?> = ArrayList()
        mentionedNames.add(ma.getActor().uniqueName) // Don't mention the author of this note
        val mentions = MyStringBuilder()
        for (actor in toMention) {
            if (actor.isEmpty()) continue
            val name = actor.getUniqueName()
            if (!name.isNullOrEmpty() && !mentionedNames.contains(name)) {
                mentionedNames.add(name)
                val mentionText = "@$name"
                if (getContent().isNullOrEmpty() || !(getContent() + " ").contains("$mentionText ")) {
                    mentions.withSpace(mentionText)
                }
            }
        }
        if (mentions.nonEmpty()) {
            setContent(mentions.toString() + " " + getContent(), TextMediaType.HTML)
        }
        return this
    }

    fun getInReplyToNoteId(): Long {
        return activity.getNote().inReplyTo.note.noteId
    }

    fun appendMentionedActorToText(mentionedActor: Actor?): NoteEditorData? {
        val name = mentionedActor.getUniqueName()
        if (!name.isNullOrEmpty()) {
            var bodyText2 = "@$name "
            if (!getContent().isNullOrEmpty() && !(getContent() + " ").contains(bodyText2)) {
                bodyText2 = getContent().trim { it <= ' ' } + " " + bodyText2
            }
            setContent(bodyText2, TextMediaType.HTML)
        }
        return this
    }

    fun addToAudience(actorId: Long): NoteEditorData? {
        return addToAudience(Actor.Companion.load( MyContextHolder.myContextHolder.getNow(), actorId))
    }

    fun addToAudience(actor: Actor?): NoteEditorData? {
        activity.getNote().audience().add(actor)
        return this
    }

    fun getVisibility(): Visibility? {
        return activity.getNote().audience().visibility
    }

    fun setPublicAndFollowers(isPublic: Boolean, isFollowers: Boolean): NoteEditorData? {
        if (canChangeVisibility()) {
            activity.getNote().audience().withVisibility(Visibility.Companion.fromCheckboxes(isPublic, canChangeIsFollowers() && isFollowers))
        }
        return this
    }

    fun setTimeline(timeline: Timeline?): NoteEditorData? {
        this.timeline = timeline
        return this
    }

    fun setName(name: String?): NoteEditorData? {
        activity.getNote().name = name
        return this
    }

    fun setSummary(summary: String?): NoteEditorData? {
        activity.getNote().summary = summary
        return this
    }

    fun canChangeVisibility(): Boolean {
        return (ma.getOrigin().originType.visibilityChangeAllowed
                && (getInReplyToNoteId() == 0L || ma.getOrigin().originType.isPrivateNoteAllowsReply))
    }

    fun canChangeIsFollowers(): Boolean {
        return (ma.getOrigin().originType.isFollowersChangeAllowed
                && (getInReplyToNoteId() == 0L || ma.getOrigin().originType.isPrivateNoteAllowsReply))
    }

    fun getSensitive(): Boolean {
        return activity.getNote().isSensitive
    }

    fun setSensitive(isSensitive: Boolean): NoteEditorData? {
        if (canChangeIsSensitive()) {
            activity.getNote().isSensitive = isSensitive
        }
        return this
    }

    fun canChangeIsSensitive(): Boolean {
        return ma.getOrigin().originType.isSensitiveChangeAllowed
    }

    fun copySensitiveProperty(): NoteEditorData? {
        if (MyQuery.isSensitive(getInReplyToNoteId())) {
            activity.getNote().isSensitive = true
            StringUtil.optNotEmpty(MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, getInReplyToNoteId()))
                    .ifPresent { summary: String? -> setSummary(summary) }
        }
        return this
    }

    companion object {
        val TAG: String? = NoteEditorData::class.java.simpleName
        val EMPTY = newEmpty(MyAccount.Companion.EMPTY)
        private fun toActivity(ma: MyAccount, noteId: Long, andLoad: Boolean): AActivity {
            val activity: AActivity
            if (noteId == 0L || !andLoad) {
                activity = AActivity.Companion.newPartialNote(ma.actor, ma.actor, "", System.currentTimeMillis(),
                        DownloadStatus.DRAFT)
            } else {
                val noteOid = MyQuery.noteIdToStringColumnValue(NoteTable.NOTE_OID, noteId)
                activity = AActivity.Companion.newPartialNote(ma.actor,
                        ma.actor, noteOid,
                        System.currentTimeMillis(),
                        DownloadStatus.Companion.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId)))
                activity.id = MyQuery.oidToId(ma.origin.myContext, OidEnum.ACTIVITY_OID,
                        activity.accountActor.origin.id,
                        activity.oid)
                if (activity.id == 0L) {
                    activity.id = MyQuery.noteIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, noteId)
                }
            }
            activity.note.noteId = noteId
            return activity
        }

        fun newEmpty(myAccount: MyAccount?): NoteEditorData? {
            return newReplyTo(0, myAccount)
        }

        fun newReplyTo(inReplyToNoteId: Long, myAccount: MyAccount?): NoteEditorData? {
            return NoteEditorData(myAccount.getValidOrCurrent( MyContextHolder.myContextHolder.getNow()), 0, true,
                    inReplyToNoteId, inReplyToNoteId != 0L)
        }

        fun load(myContext: MyContext?, noteId: Long?): NoteEditorData? {
            val authorId = MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, noteId)
            val ma = myContext.accounts().fromActorId(authorId).getValidOrCurrent(myContext)
            return NoteEditorData(ma, noteId, false, 0, true)
        }

        fun recreateKnownAudience(activity: AActivity?) {
            val note = activity.getNote()
            if (note === Note.Companion.EMPTY) return
            val audience = Audience(activity.accountActor.origin).withVisibility(note.audience().visibility)
            audience.add(note.inReplyTo.actor)
            audience.addActorsFromContent(note.content, activity.getAuthor(), note.inReplyTo.actor)
            note.setAudience(audience)
        }
    }

    init {
        myContext = ma.getOrigin().myContext
        this.activity = activity
    }
}