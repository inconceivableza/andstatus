package org.andstatus.app.service

import org.andstatus.app.account.MyAccountTest
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.HttpConnectionStub
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TriState
import org.junit.Assert

class MyServiceTestHelper : MyServiceEventsListener {
    @Volatile
    private var serviceConnector: MyServiceEventsReceiver? = null

    @Volatile
    private var httpConnectionStub: HttpConnectionStub? = null

    @Volatile
    var connectionInstanceId: Long = 0

    @Volatile
    var listenedCommand: CommandData = CommandData.Companion.EMPTY
        set(value) {
            field = value
            MyLog.v(this, "setListenedCommand; " + value)
        }

    @Volatile
    var executionStartCount: Long = 0

    @Volatile
    var executionEndCount: Long = 0

    @Volatile
    var serviceStopped = false
    private var myContext: MyContext = MyContextHolder.myContextHolder.getNow()

    fun setUp(accountName: String?) {
        MyLog.i(this, "setUp started")
        try {
            MyServiceManager.setServiceUnavailable()
            MyServiceManager.stopService()
            MyAccountTest.fixPersistentAccounts(myContext)
            val isSingleStubbedInstance = accountName.isNullOrEmpty()
            if (isSingleStubbedInstance) {
                httpConnectionStub = HttpConnectionStub()
                TestSuite.setHttpConnectionStubInstance(httpConnectionStub)
                MyContextHolder.myContextHolder.getBlocking().setExpired { this::class.simpleName + " setUp" }
            }
            myContext = MyContextHolder.myContextHolder.initialize(myContext.context, this).getBlocking()
            if (!myContext.isReady) {
                val msg = "Context is not ready after the initialization, repeating... $myContext"
                MyLog.w(this, msg)
                myContext.setExpired { this::class.simpleName + msg }
                myContext = MyContextHolder.myContextHolder.initialize(myContext.context, this).getBlocking()
                Assert.assertEquals("Context should be ready", true, myContext.isReady)
            }
            MyServiceManager.setServiceUnavailable()
            Assert.assertTrue("Couldn't stop MyService", stopService(false))
            TestSuite.getMyContextForTest().connectionState = ConnectionState.WIFI
            if (!isSingleStubbedInstance) {
                httpConnectionStub = ConnectionStub.newFor(accountName).getHttpStub()
            }
            connectionInstanceId = httpConnectionStub?.getInstanceId() ?: 0
            serviceConnector = MyServiceEventsReceiver(myContext, this).also {
                it.registerReceiver(myContext.context)
            }
            httpConnectionStub?.clearData()
            Assert.assertTrue(TestSuite.setAndWaitForIsInForeground(false))
        } catch (e: Exception) {
            MyLog.e(this, "setUp", e)
            Assert.fail(MyLog.getStackTrace(e))
        } finally {
            MyLog.i(this, "setUp ended instanceId=$connectionInstanceId")
        }
    }

    private fun dropQueues() {
        myContext.queues.clear()
    }

    fun sendListenedCommand() {
        MyServiceManager.Companion.sendCommandIgnoringServiceAvailability(listenedCommand)
    }

    /** @return true if execution started
     */
    fun waitForStartOfCommandExecution(logMsg: String?, count0: Long, expectStarted: TriState): Boolean {
        val method = this::waitForStartOfCommandExecution.name + " " + logMsg
        val stopWatch: StopWatch = StopWatch.createStarted()
        val criteria = expectStarted.select(
            "check if count > $count0",
            "check for no new execution, count0 = $count0",
            "just waiting, count0 = $count0"
        )
        MyLog.v(this, "$method; started, count=$executionStartCount, $criteria, $listenedCommand")
        var found = false
        var locEvent = "none"
        for (pass in 0..999) {
            if (executionStartCount > count0) {
                found = true
                locEvent = "count: $executionStartCount > $count0"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        val logMsgEnd = "$method; ended, found=$found, count=$executionStartCount, $criteria; " +
                "waiting ended on:$locEvent, ${stopWatch.time} ms, $listenedCommand"
        MyLog.v(this, logMsgEnd)
        if (expectStarted != TriState.UNKNOWN) {
            Assert.assertEquals(logMsgEnd, expectStarted.toBoolean(false), found)
        }
        return found
    }

    fun waitForEndOfCommandExecution(count0: Long): Boolean {
        val method = this::waitForEndOfCommandExecution.name
        val stopWatch: StopWatch = StopWatch.createStarted()
        var found = false
        var locEvent = "none"
        for (pass in 0..999) {
            if (executionEndCount > count0) {
                found = true
                locEvent = "count: $executionEndCount > $count0"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        MyLog.v(
            this, method + " ended " +
                " " + found + ", event:" + locEvent + ", count0=" + count0 +
                ", ${stopWatch.time} ms, " + listenedCommand
        )
        return found
    }

    fun waitForCondition(predicate: MyServiceTestHelper.() -> Boolean): Boolean {
        val method = "waitForCondition"
        val stopWatch: StopWatch = StopWatch.createStarted()
        var found = false
        var locEvent = "none"
        for (pass in 0..999) {
            if (predicate(this)) {
                found = true
                locEvent = "matched"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        MyLog.v(this, "$method ended, matched:$found, event:$locEvent, ${stopWatch.time} ms")
        return found
    }

    fun stopService(clearQueue: Boolean): Boolean {
        MyServiceManager.stopService()
        return waitForServiceStopped(clearQueue)
    }

    fun waitForServiceStopped(clearQueue: Boolean): Boolean {
        val method = "waitForServiceStopped"
        val stopWatch: StopWatch = StopWatch.createStarted()
        MyLog.v(this, "$method started")
        var stopped = false
        var prevCheckTime = 0L
        do {
            if (serviceStopped) {
                if (clearQueue) {
                    dropQueues()
                }
                stopped = true
                break
            }
            if (DbUtils.waitMs(method, 100)) break
            if (stopWatch.time > prevCheckTime) {
                prevCheckTime += 1000
                if (MyServiceManager.getServiceState() == MyServiceState.STOPPED) {
                    stopped = true
                    break
                }
            }
        } while (stopWatch.notPassedSeconds(130)) // TODO: fix org.andstatus.app.net.http.MyHttpClientFactory to decrease this
        MyLog.v(this, method + " ended, " + (if (stopped) "stopped" else "didn't stop") +
                ", ${stopWatch.time} ms")
        return stopped
    }

    override fun onReceive(commandData: CommandData, myServiceEvent: MyServiceEvent) {
        var locEvent = "ignored"
        when (myServiceEvent) {
            MyServiceEvent.BEFORE_EXECUTING_COMMAND -> {
                if (commandData == listenedCommand) {
                    executionStartCount++
                    locEvent = "execution started"
                }
                serviceStopped = false
            }
            MyServiceEvent.AFTER_EXECUTING_COMMAND -> if (commandData == listenedCommand) {
                executionEndCount++
                locEvent = "execution ended"
            }
            MyServiceEvent.ON_STOP -> {
                serviceStopped = true
                locEvent = "service stopped"
            }
            else -> {
            }
        }
        MyLog.v(
            this, "onReceive; " + locEvent + ", " + commandData + ", event:" + myServiceEvent +
                    ", requestsCounter:" + httpConnectionStub?.getRequestsCounter()
        )
    }

    fun tearDown() {
        MyLog.v(this, "tearDown started")
        dropQueues()
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true)
        serviceConnector?.unregisterReceiver(myContext.context)
        TestSuite.clearHttpStubs()
        TestSuite.getMyContextForTest().connectionState = ConnectionState.UNKNOWN
        MyContextHolder.myContextHolder.getBlocking().accounts.initialize()
        MyContextHolder.myContextHolder.getBlocking().timelines.initialize()
        MyServiceManager.Companion.setServiceAvailable()
        MyLog.v(this, "tearDown ended")
    }

    fun getHttp(): HttpConnectionStub {
        return httpConnectionStub ?: throw IllegalStateException("No httpConnectionStub")
    }
}
