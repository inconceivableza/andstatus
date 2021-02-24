/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data.converter

import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.converter.ConvertOneStep

import org.andstatus.app.util.StringUtil
import org.andstatus.app.os.MyAsyncTask.PoolEnum
import android.os.AsyncTask
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.InstanceId
import kotlin.jvm.Volatile
import org.andstatus.app.os.ExceptionsCounter
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteDatabaseLockedException
import org.andstatus.app.util.RelativeTime
import android.os.Looper
import io.vavr.control.Try
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.os.UiThreadExecutor
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.util.TryUtils
import org.andstatus.app.context.MyContextHolder
import org.acra.ACRA
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.R
import org.andstatus.app.os.NonUiThreadExecutor
import org.andstatus.app.net.http.HttpConnectionOAuth
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.extractors.TokenExtractor
import com.github.scribejava.core.model.OAuth2AccessToken
import org.andstatus.app.net.http.MyOAuth2AccessTokenJsonExtractor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.http.HttpConnectionData
import com.github.scribejava.core.model.Verb
import org.andstatus.app.service.ConnectionRequired
import org.json.JSONObject
import org.andstatus.app.util.UriUtils
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.SslModeEnum
import oauth.signpost.OAuthConsumer
import kotlin.Throws
import org.andstatus.app.net.http.ConnectionException
import oauth.signpost.OAuthProvider
import com.github.scribejava.core.oauth.OAuth20Service
import org.andstatus.app.net.http.HttpConnectionInterface
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.util.UrlUtils
import org.json.JSONArray
import io.vavr.control.CheckedFunction
import org.json.JSONException
import org.andstatus.app.util.JsonUtils
import org.json.JSONTokener
import org.andstatus.app.net.http.HttpConnectionUtils
import org.andstatus.app.net.http.OAuthClientKeysStrategy
import org.andstatus.app.net.http.OAuthClientKeys
import org.andstatus.app.net.http.OAuthClientKeysOpenSource
import org.andstatus.app.net.http.OAuthClientKeysDynamic
import org.andstatus.app.account.AccountName
import org.andstatus.app.account.AccountDataReader
import org.andstatus.app.util.TriState
import org.andstatus.app.data.MyContentType
import org.andstatus.app.account.AccountConnectionData
import kotlin.jvm.JvmOverloads
import android.content.res.Resources.NotFoundException
import org.andstatus.app.net.http.HttpConnectionApacheSpecific
import org.andstatus.app.net.http.HttpConnectionApacheCommon
import cz.msebera.android.httpclient.client.methods.HttpPost
import org.andstatus.app.net.http.ApacheHttpClientUtils
import cz.msebera.android.httpclient.HttpEntity
import cz.msebera.android.httpclient.client.methods.HttpGet
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.net.http.HttpConnectionEmpty
import org.andstatus.app.net.http.HttpConnectionUtils.ReadChecker
import org.andstatus.app.service.ConnectionState
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory
import cz.msebera.android.httpclient.config.RegistryBuilder
import cz.msebera.android.httpclient.conn.socket.PlainConnectionSocketFactory
import org.andstatus.app.net.http.TlsSniSocketFactory
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager
import cz.msebera.android.httpclient.client.config.RequestConfig
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder
import cz.msebera.android.httpclient.impl.client.HttpClients
import cz.msebera.android.httpclient.conn.socket.LayeredConnectionSocketFactory
import android.net.SSLCertificateSocketFactory
import cz.msebera.android.httpclient.HttpHost
import org.andstatus.app.net.http.MultipartFormEntityBytes
import cz.msebera.android.httpclient.protocol.HTTP
import android.content.ContentResolver
import cz.msebera.android.httpclient.message.BasicNameValuePair
import org.andstatus.app.net.http.MisconfiguredSslHttpClientFactory
import org.andstatus.app.net.http.MyHttpClientFactory
import org.andstatus.app.net.http.OAuthClientKeysSecret
import org.andstatus.app.util.SharedPreferencesUtil
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer
import cz.msebera.android.httpclient.impl.client.BasicResponseHandler
import oauth.signpost.exception.OAuthMessageSignerException
import oauth.signpost.exception.OAuthExpectationFailedException
import oauth.signpost.exception.OAuthCommunicationException
import cz.msebera.android.httpclient.HttpVersion
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity
import cz.msebera.android.httpclient.StatusLine
import oauth.signpost.basic.DefaultOAuthProvider
import org.andstatus.app.net.http.HttpConnectionOAuthJavaNet
import oauth.signpost.basic.DefaultOAuthConsumer
import org.apache.commons.lang3.tuple.ImmutablePair
import org.andstatus.app.net.http.HttpConnectionOAuth2JavaNet
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig
import com.github.scribejava.core.builder.ServiceBuilder
import org.andstatus.app.net.http.OAuthApi20
import com.github.scribejava.core.model.OAuthConstants
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor
import cz.msebera.android.httpclient.conn.scheme.SchemeRegistry
import cz.msebera.android.httpclient.conn.scheme.Scheme
import cz.msebera.android.httpclient.conn.scheme.PlainSocketFactory
import cz.msebera.android.httpclient.conn.ClientConnectionManager
import cz.msebera.android.httpclient.impl.conn.tsccm.ThreadSafeClientConnManager
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient
import cz.msebera.android.httpclient.params.BasicHttpParams
import cz.msebera.android.httpclient.params.HttpProtocolParams
import org.andstatus.app.net.social.pumpio.PObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.pumpio.PActivityType
import org.andstatus.app.net.social.pumpio.ConnectionPumpio
import org.andstatus.app.net.social.AActivity
import io.vavr.control.CheckedPredicate
import org.andstatus.app.net.social.Actor
import org.andstatus.app.actor.GroupType
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.net.social.InputTimelinePage
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.Audience
import org.andstatus.app.util.ObjectOrId
import org.andstatus.app.net.social.AObject
import org.andstatus.app.util.LazyVal
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.util.MyHtml
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import android.provider.BaseColumns
import org.andstatus.app.data.DbUtils
import org.andstatus.app.account.MyAccount
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.net.social.ActorEndpoints
import org.andstatus.app.data.AvatarFile
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.util.NullUtil
import org.andstatus.app.origin.ActorReference
import org.andstatus.app.context.ActorInTimeline
import org.andstatus.app.data.ActorSql
import org.andstatus.app.net.social.activitypub.ApObjectType
import org.andstatus.app.net.social.activitypub.ConnectionActivityPub
import org.andstatus.app.net.social.ConnectionMastodon
import org.andstatus.app.net.social.InputActorPage
import org.andstatus.app.net.social.AJsonCollection
import io.vavr.control.CheckedConsumer
import org.andstatus.app.util.TaggedClass
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.data.MyProvider
import org.andstatus.app.database.table.AudienceTable
import org.andstatus.app.data.SqlIds
import android.content.ContentValues
import android.text.SpannableString
import android.text.Spanned
import org.andstatus.app.net.social.SpanUtil
import android.text.Spannable
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.util.HasEmpty
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadType
import org.andstatus.app.net.social.RateLimitStatus
import org.andstatus.app.net.social.ConnectionEmpty
import org.andstatus.app.net.social.ApiDebugger
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.service.FileDownloader
import org.andstatus.app.note.NoteDownloads
import org.andstatus.app.database.table.ActorEndpointTable
import org.andstatus.app.net.social.InputPage
import org.andstatus.app.net.social.ConnectionTwitterLike
import org.andstatus.app.note.KeywordsFilter
import org.andstatus.app.net.social.ConnectionTheTwitter
import android.os.Build
import org.andstatus.app.net.social.ConnectionTwitterGnuSocial
import org.andstatus.app.data.checker.DataChecker
import org.andstatus.app.data.checker.CheckUsers.CheckResults
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.data.checker.CheckTimelines
import org.andstatus.app.data.checker.CheckDownloads
import org.andstatus.app.data.checker.MergeActors
import org.andstatus.app.data.checker.CheckUsers
import org.andstatus.app.data.checker.CheckConversations
import org.andstatus.app.data.checker.CheckAudience
import org.andstatus.app.data.checker.SearchIndexUpdate
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.checker.CheckAudience.FixSummary
import org.andstatus.app.data.checker.CheckDownloads.Results
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.database.table.TimelineTable
import org.andstatus.app.timeline.meta.TimelineSaver
import org.andstatus.app.data.checker.CheckConversations.NoteItem
import org.andstatus.app.data.converter.ConvertOneStep
import org.andstatus.app.data.converter.Convert15
import android.database.sqlite.SQLiteDatabase
import android.accounts.AccountManager
import org.andstatus.app.account.AccountUtils
import org.andstatus.app.data.converter.DatabaseConverterController
import org.andstatus.app.data.converter.AndroidAccountData
import org.andstatus.app.account.AccountData
import org.andstatus.app.account.CredentialsVerificationStatus
import org.andstatus.app.data.converter.AccountConverter
import org.andstatus.app.context.MyStorage
import org.andstatus.app.account.MyAccounts
import org.andstatus.app.data.converter.Convert47
import org.andstatus.app.data.converter.DatabaseConverterController.UpgradeParams
import org.andstatus.app.data.converter.ApplicationUpgradeException
import android.app.Activity
import org.andstatus.app.MyActivity
import org.andstatus.app.backup.DefaultProgressListener
import org.andstatus.app.data.converter.DatabaseConverter
import org.andstatus.app.data.converter.DatabaseConverterController.AsyncUpgrade
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import android.database.sqlite.SQLiteDoneException
import org.andstatus.app.data.TimelineSql
import org.andstatus.app.data.ActorToNote
import org.andstatus.app.database.table.UserTable
import org.andstatus.app.data.SqlWhere
import org.andstatus.app.data.DownloadFile
import org.andstatus.app.graphics.IdentifiableImageView
import org.andstatus.app.graphics.AttachedImageView
import org.andstatus.app.graphics.CachedImage
import org.andstatus.app.data.MediaFile.ImageLoader
import org.andstatus.app.graphics.CacheName
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.data.MediaFile
import org.andstatus.app.data.MediaFile.AbstractImageLoader
import android.graphics.drawable.Drawable
import org.andstatus.app.data.MediaFile.DrawableLoader
import android.content.Intent
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.data.ParsedUri
import org.andstatus.app.IntentExtra
import org.andstatus.app.data.AvatarData
import org.andstatus.app.data.DataPruner
import android.content.SharedPreferences
import org.andstatus.app.util.SelectionAndArgs
import org.andstatus.app.data.DownloadData.ConsumedSummary
import org.andstatus.app.timeline.meta.DisplayedInSelector
import org.andstatus.app.ClassInApplicationPackage
import android.content.UriMatcher
import android.content.ContentUris
import org.andstatus.app.database.table.OriginTable
import android.content.ContentProvider
import android.database.sqlite.SQLiteQueryBuilder
import org.andstatus.app.data.ProjectionMap
import org.andstatus.app.database.table.GroupMembersTable
import org.andstatus.app.data.LatestActorActivities
import org.andstatus.app.data.ActorActivity
import android.webkit.MimeTypeMap
import org.andstatus.app.data.ContentValuesUtils
import android.os.ParcelFileDescriptor
import org.andstatus.app.data.DownloadStatus.CanBeDownloaded
import android.database.sqlite.SQLiteConstraintException
import org.andstatus.app.context.DemoData
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.AttachedMediaFile
import org.andstatus.app.data.NoteForAnyAccount
import org.andstatus.app.data.AttachedImageFiles
import org.andstatus.app.data.NoteContextMenuData
import org.andstatus.app.data.DemoConversationInserter
import org.hamcrest.MatcherAssert
import org.hamcrest.CoreMatchers
import org.andstatus.app.data.DemoGnuSocialConversationInserter
import org.andstatus.app.lang.SelectableEnum
import android.widget.ArrayAdapter
import org.andstatus.app.lang.SelectableEnumList
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher
import org.andstatus.app.list.MyBaseListActivity
import android.os.Bundle
import org.andstatus.app.widget.MySwipeRefreshLayout.CanSwipeRefreshScrollUpCallback
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import org.andstatus.app.widget.MySwipeRefreshLayout
import org.andstatus.app.timeline.EmptyBaseTimelineAdapter
import org.andstatus.app.note.NoteEditor
import org.andstatus.app.note.NoteEditorCommand
import org.andstatus.app.service.MyServiceEventsBroadcaster
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.service.MyServiceEvent
import org.andstatus.app.note.NoteShare
import org.andstatus.app.note.NoteEditorContainer
import android.view.ViewGroup
import org.andstatus.app.note.NoteBodyTokenizer
import org.andstatus.app.note.NoteEditor.ScreenToggleState
import org.andstatus.app.note.NoteEditorBodyView
import android.widget.TextView
import android.view.LayoutInflater
import android.widget.RelativeLayout
import android.text.TextWatcher
import android.text.Editable
import android.widget.TextView.OnEditorActionListener
import android.view.View.OnTouchListener
import android.view.MotionEvent
import org.andstatus.app.note.SharedNote
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.actor.ActorAutoCompleteAdapter
import android.widget.LinearLayout
import android.widget.Toast
import org.andstatus.app.note.NoteSaver
import org.andstatus.app.note.NoteEditorLock
import org.andstatus.app.ActivityRequestCode
import android.provider.MediaStore
import org.andstatus.app.timeline.LoadableListActivity
import android.os.Parcelable
import org.andstatus.app.note.NoteContextMenu
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.note.BaseNoteAdapter
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.note.BaseNoteViewItem
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.note.KeywordsFilter.Keyword
import org.andstatus.app.note.ConversationLoader
import org.andstatus.app.note.ConversationLoaderFactory
import org.andstatus.app.note.ConversationViewItem
import org.andstatus.app.actor.MentionedActorsLoader
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.note.ConversationIndentImageView
import org.andstatus.app.note.NoteContextMenuItem
import org.andstatus.app.note.NoteContextMenuContainer
import org.andstatus.app.view.MyContextMenu
import org.andstatus.app.note.FutureNoteContextMenuData
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import org.andstatus.app.note.FutureNoteContextMenuData.StateForSelectedViewItem
import org.andstatus.app.timeline.ContextMenuHeader
import android.view.accessibility.AccessibilityManager
import org.andstatus.app.note.ConversationActivity
import org.andstatus.app.activity.ActivityViewItem
import android.text.style.URLSpan
import android.view.SubMenu
import org.andstatus.app.timeline.ViewItem
import android.text.Html
import org.andstatus.app.timeline.DuplicationLink
import org.andstatus.app.timeline.TimelineFilter
import org.andstatus.app.actor.ActorsLoader
import android.widget.MultiAutoCompleteTextView.Tokenizer
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.note.ConversationLoader.ReplyLevelComparator
import org.andstatus.app.note.ConversationLoader.OrderCounters
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import org.andstatus.app.timeline.TimelinePage
import org.andstatus.app.timeline.TimelineParameters
import org.andstatus.app.timeline.WhichPage
import org.andstatus.app.graphics.AvatarView
import org.andstatus.app.list.ContextMenuItem
import org.andstatus.app.account.AccountSelector
import org.andstatus.app.MyAction
import org.andstatus.app.timeline.LoadableListViewParameters
import android.content.ClipData
import org.andstatus.app.note.NoteEditorListActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import android.widget.CompoundButton
import org.andstatus.app.service.QueueViewer
import org.andstatus.app.timeline.LoadableListPosition
import android.widget.CheckBox
import org.andstatus.app.util.BundleUtils
import org.andstatus.app.note.ConversationAdapter
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.note.RecursiveConversationLoader
import org.andstatus.app.note.PrivateNotesConversationLoader
import androidx.appcompat.widget.AppCompatImageView
import android.view.View.MeasureSpec
import android.widget.ImageView.ScaleType
import org.andstatus.app.user.CachedUsersAndActors
import org.andstatus.app.util.Xslt
import org.apache.commons.lang3.text.translate.CharSequenceTranslator
import org.apache.commons.lang3.text.translate.AggregateTranslator
import org.apache.commons.lang3.text.translate.LookupTranslator
import org.apache.commons.lang3.text.translate.EntityArrays
import org.apache.commons.lang3.text.translate.NumericEntityUnescaper
import kotlin.jvm.Synchronized
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.content.ActivityNotFoundException
import android.os.Parcel
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import android.text.util.Linkify
import android.text.style.ClickableSpan
import android.text.Selection
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.ColorInt
import androidx.annotation.AttrRes
import android.content.res.Resources.Theme
import android.util.TypedValue
import org.andstatus.app.util.Permissions.PermissionType
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.DialogInterface
import android.view.WindowManager
import org.andstatus.app.util.DialogFactory.OkCancelDialogFragment
import android.widget.EditText
import org.andstatus.app.util.TypedCursorValue.CursorFieldType
import org.andstatus.app.util.TypedCursorValue
import androidx.documentfile.provider.DocumentFile
import org.andstatus.app.util.DocumentFileUtils
import org.andstatus.app.util.TamperingDetector
import android.content.pm.PackageInfo
import org.andstatus.app.util.FileDescriptorUtils
import androidx.preference.PreferenceFragmentCompat
import android.content.SharedPreferences.Editor
import org.andstatus.app.view.SelectorDialog
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView
import org.andstatus.app.view.MySimpleAdapter
import org.andstatus.app.view.EnumSelector
import android.view.View.OnCreateContextMenuListener
import org.andstatus.app.timeline.EmptyViewItem
import org.andstatus.app.context.MyTheme
import androidx.fragment.app.FragmentActivity
import android.widget.SimpleAdapter
import org.andstatus.app.SearchObjects
import android.widget.BaseAdapter
import android.widget.Filter.FilterResults
import org.andstatus.app.actor.GroupType.IsGroupLike
import org.andstatus.app.actor.GroupType.IsCollection
import org.andstatus.app.actor.GroupType.IsActorOwned
import org.andstatus.app.actor.ActorContextMenu
import org.andstatus.app.actor.ActorViewItemPopulator
import org.andstatus.app.actor.ActorsOfNoteLoader
import org.andstatus.app.actor.ActorAdapter
import org.andstatus.app.actor.ActorsScreen
import org.andstatus.app.actor.FriendsAndFollowersLoader
import org.andstatus.app.actor.ActorContextMenuItem
import org.andstatus.app.actor.ActorAutoCompleteAdapter.FilteredValues
import android.app.backup.BackupAgent
import org.andstatus.app.backup.MyBackupDescriptor
import android.app.backup.BackupDataOutput
import org.andstatus.app.backup.MyBackupDataOutput
import org.andstatus.app.backup.MyBackupAgent
import org.andstatus.app.database.DatabaseHolder
import android.app.backup.BackupDataInput
import org.andstatus.app.backup.MyBackupDataInput
import org.andstatus.app.context.MyContextState
import org.andstatus.app.FirstActivity
import org.andstatus.app.backup.BackupActivity.BackupTask
import android.provider.DocumentsContract
import org.andstatus.app.backup.MyBackupManager
import org.andstatus.app.backup.BackupActivity
import org.andstatus.app.backup.ProgressLogger.EmptyListener
import org.andstatus.app.backup.RestoreActivity.RestoreTask
import org.andstatus.app.backup.RestoreActivity
import org.andstatus.app.backup.MyBackupDataInput.BackupHeader
import android.app.ProgressDialog
import org.andstatus.app.list.MyListActivity
import org.andstatus.app.origin.OriginList
import org.andstatus.app.origin.OriginEditor
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.origin.OriginType.ApiEnum
import org.andstatus.app.origin.OriginType.NoteName
import org.andstatus.app.origin.OriginType.NoteSummary
import org.andstatus.app.origin.OriginType.PublicChangeAllowed
import org.andstatus.app.origin.OriginType.FollowersChangeAllowed
import org.andstatus.app.origin.OriginType.SensitiveChangeAllowed
import org.andstatus.app.origin.OriginType.ShortUrlLength
import org.andstatus.app.origin.OriginTwitter
import org.andstatus.app.net.http.HttpConnectionOAuthApache
import org.andstatus.app.net.http.HttpConnectionBasic
import org.andstatus.app.origin.OriginActivityPub
import org.andstatus.app.net.http.HttpConnectionOAuthMastodon
import org.andstatus.app.origin.OriginGnuSocial
import org.andstatus.app.origin.OriginMastodon
import android.widget.Spinner
import org.andstatus.app.origin.OriginEditor.AddOrigin
import org.andstatus.app.origin.DiscoveredOrigins
import org.andstatus.app.origin.OriginEditor.SaveOrigin
import android.widget.AdapterView.OnItemSelectedListener
import android.view.View.OnFocusChangeListener
import android.media.RingtoneManager
import android.media.Ringtone
import org.andstatus.app.origin.OriginSelector
import org.andstatus.app.context.MyContextImpl
import org.andstatus.app.origin.PersistentOrigins
import org.andstatus.app.service.MyServiceEventsListener
import org.andstatus.app.service.MyServiceEventsReceiver
import org.andstatus.app.origin.DiscoveredOriginList
import android.widget.AutoCompleteTextView
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import org.andstatus.app.context.MyLocale
import androidx.annotation.RawRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.andstatus.app.account.AuthenticatorService
import android.content.PeriodicSync
import com.woxthebox.draglistview.DragListView
import androidx.recyclerview.widget.LinearLayoutManager
import org.andstatus.app.account.AccountListFragment.ItemAdapter
import org.andstatus.app.account.AccountListFragment.MyDragItem
import com.woxthebox.draglistview.DragItem
import org.andstatus.app.util.MyResources
import com.woxthebox.draglistview.DragItemAdapter
import org.andstatus.app.account.AccountListFragment.ItemAdapter.AccountViewHolder
import org.andstatus.app.account.AccountSettingsActivity
import org.andstatus.app.account.DemoAccountInserter
import android.accounts.AbstractAccountAuthenticator
import android.accounts.NetworkErrorException
import android.accounts.AccountAuthenticatorResponse
import android.os.IBinder
import org.andstatus.app.account.AccountListFragment
import org.andstatus.app.account.AccountSettingsActivity.FragmentAction
import org.andstatus.app.account.AccountSettingsActivity.ResultStatus
import org.andstatus.app.account.AccountSettingsActivity.ActivityOnFinish
import org.andstatus.app.account.StateOfAccountChangeProcess
import org.andstatus.app.account.InstanceForNewAccountFragment
import org.andstatus.app.account.AccountSettingsFragment
import org.andstatus.app.origin.PersistentOriginList
import org.andstatus.app.account.AccountSettingsActivity.OAuthAcquireAccessTokenTask
import org.andstatus.app.account.AccountSettingsActivity.VerifyCredentialsTask
import org.andstatus.app.account.AccountSettingsActivity.OAuthRegisterClientTask
import org.andstatus.app.account.AccountSettingsActivity.OAuthAcquireRequestTokenTask
import oauth.signpost.exception.OAuthNotAuthorizedException
import org.andstatus.app.account.AccountSettingsWebActivity
import oauth.signpost.OAuth
import io.vavr.control.NonFatalException
import org.andstatus.app.account.AccountSettingsWebActivity.WebViewListener
import android.webkit.WebViewClient
import android.graphics.Bitmap
import org.andstatus.app.origin.DemoOriginInserter
import org.andstatus.app.context.DemoData.MyAsyncTaskDemoData
import android.os.LocaleList
import android.content.ContextWrapper
import org.andstatus.app.timeline.meta.PersistentTimelines
import org.andstatus.app.service.CommandQueue
import org.andstatus.app.notification.Notifier
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.context.ExecutionMode
import org.acra.annotation.AcraMailSender
import org.acra.annotation.AcraDialog
import org.acra.annotation.AcraCore
import android.app.Application
import org.andstatus.app.context.MyApplication
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.DatabaseErrorHandler
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import org.andstatus.app.timeline.TapOnATimelineTitleBehaviour
import android.app.backup.BackupManager
import org.andstatus.app.context.MySettingsFragment
import org.andstatus.app.context.StorageSwitch.MoveDataBetweenStoragesTask
import org.andstatus.app.context.StorageSwitch
import org.andstatus.app.context.MyFutureContext
import org.andstatus.app.syncadapter.SyncInitiator
import android.util.AndroidRuntimeException
import org.andstatus.app.HelpActivity
import org.andstatus.app.context.MySettingsGroup
import org.andstatus.app.MyActivity.OnFinishAction
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import org.andstatus.app.notification.NotificationMethodType
import org.andstatus.app.account.ManageAccountsActivity
import org.andstatus.app.timeline.meta.ManageTimelines
import androidx.preference.PreferenceViewHolder
import org.andstatus.app.service.QueueExecutors
import org.andstatus.app.service.MyService.HeartBeat
import android.os.PowerManager.WakeLock
import org.andstatus.app.service.MyService
import android.content.BroadcastReceiver
import android.content.IntentFilter
import org.andstatus.app.appwidget.AppWidgets
import android.os.PowerManager
import org.andstatus.app.service.QueueExecutor
import org.andstatus.app.service.QueueType
import org.andstatus.app.service.QueueData
import org.andstatus.app.service.CommandTimeline
import org.andstatus.app.service.CommandResult
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.service.CommandQueue.OneQueue
import org.andstatus.app.service.QueueViewerAdapter
import android.view.MenuInflater
import org.andstatus.app.service.CommandQueue.AccessorType
import org.andstatus.app.service.CommandExecutorParent
import org.andstatus.app.service.CommandExecutorStrategy
import org.andstatus.app.service.AvatarDownloader
import org.andstatus.app.service.AttachmentDownloader
import org.andstatus.app.service.MyServiceManager.MyServiceStateInTime
import org.andstatus.app.service.MyServiceManager.ServiceAvailability
import org.andstatus.app.service.TimelineSyncTracker
import org.andstatus.app.service.CommandExecutorOther
import org.andstatus.app.service.CommandExecutorGetOpenInstances
import org.andstatus.app.service.TimelineDownloaderFollowers
import org.andstatus.app.service.TimelineDownloaderOther
import org.andstatus.app.service.CommandExecutorFollowers
import android.content.SyncResult
import org.andstatus.app.service.TimelineDownloader
import org.andstatus.app.activity.ActivityContextMenu
import org.andstatus.app.note.NoteAdapter
import org.andstatus.app.activity.ActivityAdapter.LayoutType
import org.andstatus.app.activity.TimelineDataActorWrapper
import org.andstatus.app.activity.TimelineDataNoteWrapper
import org.andstatus.app.activity.TimelineDataObjActorWrapper
import org.andstatus.app.activity.ActorOfActivityContextMenu
import org.andstatus.app.activity.TimelineDataWrapper
import android.database.sqlite.SQLiteOpenHelper
import org.andstatus.app.database.DatabaseCreator
import android.util.DisplayMetrics
import org.andstatus.app.graphics.ImageCacheApi28Helper
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import org.andstatus.app.graphics.BitmapSubsetDrawable
import android.view.Display
import org.apache.commons.lang3.time.DurationFormatUtils
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.annotation.TargetApi
import android.graphics.ImageDecoder.OnHeaderDecodedListener
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Animatable
import org.andstatus.app.timeline.meta.TimelineTitle
import org.andstatus.app.timeline.meta.ManageTimelinesContextMenu
import org.andstatus.app.timeline.meta.ManageTimelinesViewItem
import org.andstatus.app.timeline.meta.ManageTimelinesViewItemComparator
import org.andstatus.app.timeline.meta.TimelineSelector
import org.andstatus.app.timeline.meta.ManageTimelinesContextMenuItem
import org.andstatus.app.timeline.ViewItemType
import org.andstatus.app.timeline.DuplicatesCollapser
import android.widget.AbsListView
import org.andstatus.app.test.SelectorActivityMock
import org.andstatus.app.actor.ActorProfileViewer
import org.andstatus.app.widget.MySearchView
import org.andstatus.app.activity.ActivityAdapter
import org.andstatus.app.timeline.TimelineViewPositionStorage
import android.view.Gravity
import org.andstatus.app.timeline.TimelineLoader
import org.andstatus.app.timeline.DuplicatesCollapser.ItemWithPage
import org.andstatus.app.timeline.DuplicatesCollapser.GroupToCollapse
import org.andstatus.app.timeline.LoadableListActivity.AsyncLoader
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.appwidget.MyAppWidgetData
import android.appwidget.AppWidgetManager
import org.andstatus.app.appwidget.MyRemoteViewData
import android.widget.RemoteViews
import android.content.ComponentName
import org.andstatus.app.appwidget.MyAppWidgetProvider
import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import org.andstatus.app.appwidget.MyAppWidgetConfigure
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import org.andstatus.app.service.MyServiceCommandsRunner
import org.andstatus.app.syncadapter.SyncService.ResourceHolder
import org.andstatus.app.syncadapter.SyncAdapter
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.NotificationChannel
import androidx.appcompat.app.AppCompatActivity
import android.view.InflateException
import androidx.viewpager.widget.ViewPager
import androidx.fragment.app.FragmentPagerAdapter
import org.andstatus.app.HelpActivity.LogoFragment
import androidx.viewpager.widget.PagerAdapter
import org.andstatus.app.FirstActivity.NeedToStart
import cz.msebera.android.httpclient.client.methods.HttpUriRequest
import cz.msebera.android.httpclient.HttpEntityEnclosingRequest
import oauth.signpost.AbstractOAuthConsumer
import oauth.signpost.commonshttp.HttpRequestAdapter
import oauth.signpost.AbstractOAuthProvider
import oauth.signpost.commonshttp.HttpResponseAdapter
import org.junit.rules.TestRule
import org.apache.geode.test.junit.ConditionalIgnore
import org.apache.geode.test.junit.rules.ConditionalIgnoreRule
import org.apache.geode.test.junit.IgnoreCondition
import org.apache.geode.test.junit.support.IgnoreConditionEvaluationException
import org.apache.geode.test.junit.support.DefaultIgnoreCondition
import kotlin.reflect.KClass
import org.andstatus.app.util.RawResourceUtils
import org.junit.Before
import org.andstatus.app.net.social.ConnectionMock
import org.junit.After
import org.hamcrest.core.StringStartsWith
import org.hamcrest.Matchers
import org.andstatus.app.util.UriUtilsTest
import org.andstatus.app.net.social.activitypub.VerifyCredentialsActivityPubTest
import org.hamcrest.text.IsEmptyString
import org.andstatus.app.net.http.HttpConnectionMock
import org.andstatus.app.tests.R
import org.andstatus.app.util.MyHtmlTest
import org.andstatus.app.net.social.ConnectionGnuSocialTest
import org.andstatus.app.service.AttachmentDownloaderTest
import org.andstatus.app.data.HtmlContentTester
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.note.NoteEditorTest
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.timeline.ListScreenTestHelper
import org.andstatus.app.note.NoteEditorTest.ClipboardReader
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.action.ReplaceTextAction
import org.andstatus.app.util.ScreenshotOnFailure
import io.vavr.control.CheckedRunnable
import android.app.Instrumentation.ActivityMonitor
import org.andstatus.app.service.MyServiceTestHelper
import org.andstatus.app.note.NoteViewItemTest
import org.andstatus.app.note.NoteEditorTwitterTest
import androidx.test.espresso.action.ViewActions
import org.andstatus.app.util.MyLogTest
import org.andstatus.app.util.MyLogTest.LazyClass
import androidx.test.espresso.ViewAction
import android.widget.Checkable
import org.andstatus.app.context.ActivityTest
import android.text.SpannedString
import org.andstatus.app.actor.ActorsScreenTest
import org.andstatus.app.actor.FollowersScreen
import androidx.test.rule.GrantPermissionRule
import org.andstatus.app.backup.MyBackupAgentTest
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import org.andstatus.app.account.MyAccountTest
import org.andstatus.app.context.MyContextTestImpl
import android.app.KeyguardManager
import org.andstatus.app.context.NoScreenSupport
import androidx.test.rule.ActivityTestRule
import org.andstatus.app.context.CompletableFutureTest.TestData
import org.andstatus.app.service.MyServiceTest
import org.andstatus.app.service.AvatarDownloaderTest
import org.andstatus.app.service.RepeatingFailingCommandTest
import org.hamcrest.core.Is
import org.hamcrest.core.IsNot
import org.andstatus.app.timeline.meta.TimelineSyncTrackerTest
import org.andstatus.app.timeline.TimelinePositionTest
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.timeline.TimeLineActivityLayoutToggleTest
import org.andstatus.app.appwidget.MyAppWidgetProviderTest.DateTest
import org.andstatus.app.appwidget.MyAppWidgetProviderTest
import org.andstatus.app.notification.NotifierTest
import org.andstatus.app.ActivityTestHelper.MenuItemClicker
import org.andstatus.app.MenuItemMock

internal class Convert27 : ConvertOneStep() {
    protected override fun execute2() {

        // Table creation statements for v.37
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN NOT NULL DEFAULT 1,ssl_mode INTEGER NOT NULL DEFAULT 1,allow_html BOOLEAN NOT NULL DEFAULT 1,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0,mention_as_webfinger_id INTEGER NOT NULL DEFAULT 3,use_legacy_http INTEGER NOT NULL DEFAULT 3,in_combined_global_search BOOLEAN NOT NULL DEFAULT 1,in_combined_public_reload BOOLEAN NOT NULL DEFAULT 1)"
        sql = "CREATE TABLE note (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,note_oid TEXT NOT NULL,note_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER NOT NULL DEFAULT 0,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,note_author_id INTEGER NOT NULL DEFAULT 0,in_reply_to_note_id INTEGER,in_reply_to_actor_id INTEGER,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,note_ins_date INTEGER NOT NULL,note_updated_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_note_origin ON note (origin_id, note_oid)"
        sql = "CREATE INDEX idx_note_in_reply_to_note_id ON note (in_reply_to_note_id)"
        sql = "CREATE INDEX idx_note_conversation_id ON note (conversation_id)"
        sql = "CREATE INDEX idx_conversation_oid ON note (origin_id, conversation_oid)"
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_known_as TEXT NOT NULL DEFAULT '',is_my_user INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE INDEX idx_my_user ON user (is_my_user)"
        sql = "CREATE TABLE actor (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL,origin_id INTEGER NOT NULL,actor_oid TEXT NOT NULL,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,actor_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,notes_count INTEGER NOT NULL DEFAULT 0,favorited_count INTEGER NOT NULL DEFAULT 0,following_count INTEGER NOT NULL DEFAULT 0,followers_count INTEGER NOT NULL DEFAULT 0,actor_created_date INTEGER NOT NULL DEFAULT 0,actor_updated_date INTEGER NOT NULL DEFAULT 0,actor_ins_date INTEGER NOT NULL,actor_activity_id INTEGER NOT NULL DEFAULT 0,actor_activity_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_actor_origin ON actor (origin_id, actor_oid)"
        sql = "CREATE INDEX idx_actor_user ON actor (user_id)"
        sql = "CREATE INDEX idx_actor_webfinger ON actor (webfinger_id)"
        sql = "CREATE TABLE audience (actor_id INTEGER NOT NULL,note_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (note_id ASC, actor_id ASC))"
        sql = "CREATE INDEX idx_audience_actor ON audience (actor_id)"
        sql = "CREATE INDEX idx_audience_note ON audience (note_id)"
        sql = "CREATE TABLE friendship (actor_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (actor_id, friend_id))"
        sql = "CREATE INDEX idx_followers ON friendship (friend_id, actor_id)"
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,note_id INTEGER NOT NULL DEFAULT 0,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER NOT NULL DEFAULT 0,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)"
        sql = "CREATE INDEX idx_download_actor ON download (actor_id, download_status)"
        sql = "CREATE INDEX idx_download_note ON download (note_id, content_type, download_status)"
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type TEXT NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,actor_in_timeline TEXT,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,is_synced_automatically BOOLEAN NOT NULL DEFAULT 0,displayed_in_selector INTEGER NOT NULL DEFAULT 0,selector_order INTEGER NOT NULL DEFAULT 0,sync_succeeded_date INTEGER NOT NULL DEFAULT 0,sync_failed_date INTEGER NOT NULL DEFAULT 0,error_message TEXT,synced_times_count INTEGER NOT NULL DEFAULT 0,sync_failed_times_count INTEGER NOT NULL DEFAULT 0,downloaded_items_count INTEGER NOT NULL DEFAULT 0,new_items_count INTEGER NOT NULL DEFAULT 0,count_since INTEGER NOT NULL DEFAULT 0,synced_times_count_total INTEGER NOT NULL DEFAULT 0,sync_failed_times_count_total INTEGER NOT NULL DEFAULT 0,downloaded_items_count_total INTEGER NOT NULL DEFAULT 0,new_items_count_total INTEGER NOT NULL DEFAULT 0,youngest_position TEXT,youngest_item_date INTEGER NOT NULL DEFAULT 0,youngest_synced_date INTEGER NOT NULL DEFAULT 0,oldest_position TEXT,oldest_item_date INTEGER NOT NULL DEFAULT 0,oldest_synced_date INTEGER NOT NULL DEFAULT 0,visible_item_id INTEGER NOT NULL DEFAULT 0,visible_y INTEGER NOT NULL DEFAULT 0,visible_oldest_date INTEGER NOT NULL DEFAULT 0,last_changed_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER NOT NULL,activity_note_id INTEGER NOT NULL,activity_actor_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,interacted INTEGER NOT NULL DEFAULT 0,interaction_event INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,notified_actor_id INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)"
        sql = "CREATE INDEX idx_activity_message ON activity (activity_note_id)"
        sql = "CREATE INDEX idx_activity_actor ON activity (activity_actor_id)"
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)"
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)"
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (actor_id, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_notified_actor ON activity (notified, notified_actor_id)"
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)"
        sql = "CREATE INDEX idx_activity_interacted_timeline ON activity (interacted, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_interacted_actor ON activity (interacted, notified_actor_id)"
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN NOT NULL DEFAULT 0,manually_launched BOOLEAN NOT NULL DEFAULT 0,timeline_id INTEGER NOT NULL DEFAULT 0,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,actor_id INTEGER NOT NULL DEFAULT 0,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,item_id INTEGER NOT NULL DEFAULT 0,username TEXT,last_executed_date INTEGER NOT NULL DEFAULT 0,execution_count INTEGER NOT NULL DEFAULT 0,retries_left INTEGER NOT NULL DEFAULT 0,num_auth_exceptions INTEGER NOT NULL DEFAULT 0,num_io_exceptions INTEGER NOT NULL DEFAULT 0,num_parse_exceptions INTEGER NOT NULL DEFAULT 0,error_message TEXT,downloaded_count INTEGER NOT NULL DEFAULT 0,progress_text TEXT)"
        progressLogger.logProgress("$stepTitle: Converting notes")
        dropOldIndex("idx_msg_origin")
        dropOldIndex("idx_msg_in_reply_to_msg_id")
        dropOldIndex("idx_msg_conversation_id")
        dropOldIndex("idx_conversation_oid")
        sql = "CREATE TABLE note (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,note_oid TEXT NOT NULL,note_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER NOT NULL DEFAULT 0,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,note_author_id INTEGER NOT NULL DEFAULT 0,in_reply_to_note_id INTEGER,in_reply_to_actor_id INTEGER,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,note_ins_date INTEGER NOT NULL,note_updated_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_note_origin ON note (origin_id, note_oid)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_note_in_reply_to_note_id ON note (in_reply_to_note_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_note_conversation_id ON note (conversation_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_conversation_oid ON note (origin_id, conversation_oid)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO note (" +
                "_id,origin_id,note_oid,note_status,conversation_id,conversation_oid,url,body,body_to_search,via,note_author_id,in_reply_to_note_id,in_reply_to_actor_id,private,favorited,reblogged,favorite_count,reblog_count,reply_count,note_ins_date,note_updated_date" +
                ") SELECT " +
                "_id,origin_id, msg_oid, msg_status,conversation_id,conversation_oid,url,body,body_to_search,via, msg_author_id, in_reply_to_msg_id, in_reply_to_user_id,private,favorited,reblogged,favorite_count,reblog_count,reply_count, msg_ins_date, msg_updated_date" +
                " FROM msg"
        DbUtils.execSQL(db, sql)
        dropOldTable("msg")
        progressLogger.logProgress("$stepTitle: Converting actors")
        dropOldIndex("idx_user_origin")
        sql = "ALTER TABLE user RENAME TO olduser"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_known_as TEXT NOT NULL DEFAULT '',is_my_user INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_my_user ON user (is_my_user)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE actor (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL,origin_id INTEGER NOT NULL,actor_oid TEXT NOT NULL,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,actor_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,notes_count INTEGER NOT NULL DEFAULT 0,favorited_count INTEGER NOT NULL DEFAULT 0,following_count INTEGER NOT NULL DEFAULT 0,followers_count INTEGER NOT NULL DEFAULT 0,actor_created_date INTEGER NOT NULL DEFAULT 0,actor_updated_date INTEGER NOT NULL DEFAULT 0,actor_ins_date INTEGER NOT NULL,actor_activity_id INTEGER NOT NULL DEFAULT 0,actor_activity_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_actor_origin ON actor (origin_id, actor_oid)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_actor_user ON actor (user_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_actor_webfinger ON actor (webfinger_id)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO actor (" +
                "_id,user_id,origin_id,actor_oid,username,webfinger_id,real_name,actor_description,location,profile_url,homepage,avatar_url,banner_url,notes_count,favorited_count,following_count,followers_count,actor_created_date,actor_updated_date,actor_ins_date,actor_activity_id,actor_activity_date" +
                ") SELECT " +
                "_id,    _id,origin_id, user_oid,username,webfinger_id,real_name, user_description,location,profile_url,homepage,avatar_url,banner_url,  msg_count,favorited_count,following_count,followers_count, user_created_date, user_updated_date, user_ins_date, user_activity_id, user_activity_date" +
                " FROM olduser"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO user (" +
                "_id, user_known_as, is_my_user" +
                ") SELECT " +
                "_id,  webfinger_id, 0" +
                " FROM actor"
        DbUtils.execSQL(db, sql)
        dropOldTable("olduser")
        progressLogger.logProgress("$stepTitle: Converting audience")
        dropOldIndex("idx_audience_user")
        dropOldIndex("idx_audience_msg")
        sql = "ALTER TABLE audience RENAME TO oldaudience"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE audience (actor_id INTEGER NOT NULL,note_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (note_id ASC, actor_id ASC))"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_audience_actor ON audience (actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_audience_note ON audience (note_id)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO audience (" +
                "actor_id,note_id" +
                ") SELECT " +
                " user_id, msg_id" +
                " FROM oldaudience"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldaudience")
        progressLogger.logProgress("$stepTitle: Converting download")
        dropOldIndex("idx_download_user")
        dropOldIndex("idx_download_msg")
        sql = "ALTER TABLE download RENAME TO olddownload"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,note_id INTEGER NOT NULL DEFAULT 0,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER NOT NULL DEFAULT 0,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_download_actor ON download (actor_id, download_status)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_download_note ON download (note_id, content_type, download_status)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO download (" +
                "_id,download_type,actor_id,note_id,content_type,valid_from,url,loaded_date,download_status,file_name" +
                ") SELECT " +
                "_id,download_type, user_id, msg_id,content_type,valid_from,url,loaded_date,download_status,file_name" +
                " FROM olddownload"
        DbUtils.execSQL(db, sql)
        dropOldTable("olddownload")
        progressLogger.logProgress("$stepTitle: Converting activity")
        dropOldIndex("idx_activity_origin")
        dropOldIndex("idx_activity_message")
        dropOldIndex("idx_activity_user")
        dropOldIndex("idx_activity_activity")
        dropOldIndex("idx_activity_timeline")
        dropOldIndex("idx_activity_actor_timeline")
        dropOldIndex("idx_activity_subscribed_timeline")
        dropOldIndex("idx_activity_notified_timeline")
        dropOldIndex("idx_activity_new_notification")
        sql = "ALTER TABLE activity RENAME TO oldactivity"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER NOT NULL,activity_note_id INTEGER NOT NULL,activity_actor_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,interacted INTEGER NOT NULL DEFAULT 0,interaction_event INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,notified_actor_id INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_message ON activity (activity_note_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_actor ON activity (activity_actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (actor_id, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_notified_actor ON activity (notified, notified_actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_interacted_timeline ON activity (interacted, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_interacted_actor ON activity (interacted, notified_actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO activity (" +
                "_id,activity_origin_id,activity_oid,account_id,activity_type,actor_id,activity_note_id,activity_actor_id,obj_activity_id,subscribed" +
                ",interacted,interaction_event" +
                ",notified_actor_id" +
                ",notified,new_notification_event,activity_ins_date,activity_updated_date" +
                ") SELECT " +
                "_id,activity_origin_id,activity_oid,account_id,activity_type,actor_id, activity_msg_id, activity_user_id,obj_activity_id,subscribed" +
                ",notified,new_notification_event" +
                ",CASE notified WHEN 2 THEN account_id ELSE 0 END" +
                ",notified,new_notification_event,activity_ins_date,activity_updated_date" +
                " FROM oldactivity"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldactivity")
        progressLogger.logProgress("$stepTitle: Converting timeline")
        sql = "ALTER TABLE timeline RENAME TO oldtimeline"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type TEXT NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,actor_in_timeline TEXT,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,is_synced_automatically BOOLEAN NOT NULL DEFAULT 0,displayed_in_selector INTEGER NOT NULL DEFAULT 0,selector_order INTEGER NOT NULL DEFAULT 0,sync_succeeded_date INTEGER NOT NULL DEFAULT 0,sync_failed_date INTEGER NOT NULL DEFAULT 0,error_message TEXT,synced_times_count INTEGER NOT NULL DEFAULT 0,sync_failed_times_count INTEGER NOT NULL DEFAULT 0,downloaded_items_count INTEGER NOT NULL DEFAULT 0,new_items_count INTEGER NOT NULL DEFAULT 0,count_since INTEGER NOT NULL DEFAULT 0,synced_times_count_total INTEGER NOT NULL DEFAULT 0,sync_failed_times_count_total INTEGER NOT NULL DEFAULT 0,downloaded_items_count_total INTEGER NOT NULL DEFAULT 0,new_items_count_total INTEGER NOT NULL DEFAULT 0,youngest_position TEXT,youngest_item_date INTEGER NOT NULL DEFAULT 0,youngest_synced_date INTEGER NOT NULL DEFAULT 0,oldest_position TEXT,oldest_item_date INTEGER NOT NULL DEFAULT 0,oldest_synced_date INTEGER NOT NULL DEFAULT 0,visible_item_id INTEGER NOT NULL DEFAULT 0,visible_y INTEGER NOT NULL DEFAULT 0,visible_oldest_date INTEGER NOT NULL DEFAULT 0,last_changed_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO timeline (" +
                "_id,timeline_type," +
                "actor_id,actor_in_timeline,origin_id,search_query,is_synced_automatically,displayed_in_selector,selector_order,sync_succeeded_date,sync_failed_date,error_message,synced_times_count,sync_failed_times_count,downloaded_items_count,new_items_count,count_since,synced_times_count_total,sync_failed_times_count_total,downloaded_items_count_total,new_items_count_total,youngest_position,youngest_item_date,youngest_synced_date,oldest_position,oldest_item_date,oldest_synced_date,visible_item_id,visible_y,visible_oldest_date" +
                ") SELECT " +
                "_id," +
                " CASE timeline_type" +
                "   WHEN 'user' THEN 'sent'" +
                "   WHEN 'my_friends' THEN 'friends'" +
                "   WHEN 'my_followers' THEN 'followers'" +
                "   WHEN 'mentions' THEN 'interactions'" +
                "   ELSE timeline_type" +
                " END," +
                " user_id, user_in_timeline,origin_id,search_query,is_synced_automatically,displayed_in_selector,selector_order,sync_succeeded_date,sync_failed_date,error_message,synced_times_count,sync_failed_times_count,downloaded_items_count,new_items_count,count_since,synced_times_count_total,sync_failed_times_count_total,downloaded_items_count_total,new_items_count_total,youngest_position,youngest_item_date,youngest_synced_date,oldest_position,oldest_item_date,oldest_synced_date,visible_item_id,visible_y,visible_oldest_date" +
                " FROM oldtimeline"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldtimeline")
        progressLogger.logProgress("$stepTitle: Converting command")
        sql = "ALTER TABLE command RENAME TO oldcommand"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN NOT NULL DEFAULT 0,manually_launched BOOLEAN NOT NULL DEFAULT 0,timeline_id INTEGER NOT NULL DEFAULT 0,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,actor_id INTEGER NOT NULL DEFAULT 0,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,item_id INTEGER NOT NULL DEFAULT 0,username TEXT,last_executed_date INTEGER NOT NULL DEFAULT 0,execution_count INTEGER NOT NULL DEFAULT 0,retries_left INTEGER NOT NULL DEFAULT 0,num_auth_exceptions INTEGER NOT NULL DEFAULT 0,num_io_exceptions INTEGER NOT NULL DEFAULT 0,num_parse_exceptions INTEGER NOT NULL DEFAULT 0,error_message TEXT,downloaded_count INTEGER NOT NULL DEFAULT 0,progress_text TEXT)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO command (" +
                "_id,queue_type,command_code,command_created_date,command_description,in_foreground,manually_launched,timeline_id,timeline_type,account_id,actor_id,origin_id,search_query,item_id,username,last_executed_date,execution_count,retries_left,num_auth_exceptions,num_io_exceptions,num_parse_exceptions,error_message,downloaded_count,progress_text" +
                ") SELECT " +
                "_id,queue_type,command_code,command_created_date,command_description,in_foreground,manually_launched,timeline_id,timeline_type,account_id, user_id,origin_id,search_query,item_id,username,last_executed_date,execution_count,retries_left,num_auth_exceptions,num_io_exceptions,num_parse_exceptions,error_message,downloaded_count,progress_text" +
                " FROM oldcommand"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldcommand")
        progressLogger.logProgress("$stepTitle: Converting friendship")
        dropOldIndex("idx_followers")
        sql = "ALTER TABLE friendship RENAME TO oldfriendship"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE friendship (actor_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (actor_id, friend_id))"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_followers ON friendship (friend_id, actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO friendship (" +
                "actor_id,friend_id,followed" +
                ") SELECT " +
                "user_id,friend_id,followed" +
                " FROM oldfriendship"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldfriendship")
    }

    init {
        versionTo = 37
    }
}