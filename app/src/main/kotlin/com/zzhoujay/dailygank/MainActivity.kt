package com.zzhoujay.dailygank

import android.os.Bundle
import android.os.Handler
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.bumptech.glide.Glide
import com.zzhoujay.dailygank.common.Config
import com.zzhoujay.dailygank.data.DailyProvider
import com.zzhoujay.dailygank.data.DataManager
import com.zzhoujay.dailygank.data.DateProvider
import com.zzhoujay.dailygank.ui.adapter.*
import com.zzhoujay.dailygank.util.DateKit
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.async
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.util.*

class MainActivity : AppCompatActivity() {

    val dateProvider: DateProvider by lazy { DateProvider() }
    val bottomSheetCallback: TaskQueueBottomSheetCallback by lazy { TaskQueueBottomSheetCallback() }
    val handler: Handler by lazy { Handler(mainLooper) }

    var dailyProvider: DailyProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        val dailyAdapter = DailyAdapter(this)
        val loadingAdapter = LoadingAdapter(this)
        val dateAdapter = DateAdapter(this)
        val statusAdapter = StatusAdapter(Status.loading to loadingAdapter,
                Status.normal to dailyAdapter,
                Status.date to dateAdapter)
        val handlerAdapter = HandlerAdapter(this, statusAdapter)

        val bsb = BottomSheetBehavior.from(recyclerView)
        bsb.setBottomSheetCallback(bottomSheetCallback)


        handlerAdapter.onHandlerClickListener = {
            when (bsb.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> bsb.state = BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.STATE_EXPANDED -> bsb.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        fun loadNormal(date: Date? = null, immediatelyShow: Boolean = false, updateTime: Int = DataManager.NONE_UPDATE) {
            val loadStartTime = System.currentTimeMillis()
            async() {
                val c = Calendar.getInstance()
                if (date != null) {
                    c.time = date
                    dailyProvider= DailyProvider(c)
                } else {
                    if (dailyProvider == null) {
                        val dates = DataManager.load(dateProvider, updateTime = updateTime)
                        if (dates != null && dates.size > 0) {
                            c.time = dates[0]
                        }
                        dailyProvider = DailyProvider(c)
                    }
                }
                val r = DataManager.load(dailyProvider!!)
                val g = r?.typeOfGanks("福利")
                uiThread {
                    val currTime = System.currentTimeMillis()
                    val dt = currTime - loadStartTime

                    handlerAdapter.title = DateKit.friendlyFormatDate(c.time)

                    fun switchToNormal() {
                        statusAdapter.switch(Status.normal)
                        dailyAdapter.daily = r
                        if (g != null) {
                            Glide.with(this@MainActivity).load(g.url)
                                    .placeholder(R.drawable.image_default)
                                    .into(image)
                        }
                    }
                    if (!immediatelyShow && dt < Config.Const.min_load_time) {
                        handler.postDelayed({
                            switchToNormal()
                        }, Config.Const.min_load_time - dt)
                    } else {
                        switchToNormal()
                    }
                }
            }
        }

        fun loadDate(updateTime: Int = DataManager.NONE_UPDATE) {
            val loadStartTime = System.currentTimeMillis()
            async() {
                val r = DataManager.load(dateProvider, updateTime = updateTime)
                uiThread {
                    val currTime = System.currentTimeMillis()
                    val dt = currTime - loadStartTime

                    fun switchToDate() {
                        dateAdapter.dates = r
                        statusAdapter.switch(Status.date)
                    }
                    if (dt < Config.Const.min_load_time) {
                        handler.postDelayed({
                            switchToDate()
                        }, Config.Const.min_load_time - dt)
                    } else {
                        switchToDate()
                    }
                }
            }
        }

        fun switch(fromNormal: Boolean = true, date: Date? = null) {
            statusAdapter.switch(Status.loading)
            if (fromNormal) {
                loadDate()
            } else {
                loadNormal(date)
            }
        }


        fun switchWrapper(f:()->Unit){
            if (statusAdapter.currStatus.equals(Status.normal)) {
                handlerAdapter.isNormal=false
                switch()
            } else if (statusAdapter.currStatus.equals(Status.date)) {
                handlerAdapter.isNormal=true
                switch(false)
            }
        }


        dateAdapter.onItemCheckedListener = {
            if (statusAdapter.currStatus.equals(Status.normal)) {
                handlerAdapter.isNormal=false
            } else if (statusAdapter.currStatus.equals(Status.date)) {
                handlerAdapter.isNormal=true
            }
            switch(false, it)
        }

        handlerAdapter.onListClickListener = { v, i ->
            fun switchWrapper() {
                if (statusAdapter.currStatus.equals(Status.normal)) {
                    handlerAdapter.isNormal=false
                    switch()
                } else if (statusAdapter.currStatus.equals(Status.date)) {
                    handlerAdapter.isNormal=true
                    switch(false)
                }
            }
            if (bsb.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bsb.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheetCallback.addTask {
                    switchWrapper()
                }
            } else {
                switchWrapper()
            }
        }

        handlerAdapter.onListLongClickListener = {
            if (statusAdapter.currStatus.equals(Status.normal)) {
                toast(R.string.text_date)
            } else if (statusAdapter.currStatus.equals(Status.date)) {
                toast(R.string.text_normal)
            }
        }

        recyclerView.adapter = handlerAdapter

        loadNormal(immediatelyShow = true, updateTime = DataManager.ONE_DAY)
    }

    class TaskQueueBottomSheetCallback : BottomSheetBehavior.BottomSheetCallback() {
        private val tasks: Queue<(() -> Unit)> by lazy { LinkedList<(() -> Unit)>() }

        fun addTask(t: () -> Unit) {
            tasks.add (t)
        }

        override fun onSlide(p0: View, p1: Float) {
        }

        override fun onStateChanged(p0: View, p1: Int) {
            if (p1 == BottomSheetBehavior.STATE_EXPANDED) {
                if (!tasks.isEmpty()) {
                    tasks.remove().invoke()
                }
            }
        }

    }
}
