package io.github.yutatakeda06.android.durationofmusic

import android.media.MediaPlayer
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.os.HandlerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object{
        //ログに記載するタグ用の文字列
        private const val DEBUG_TAG = "DirectionSearch"
        //DirectionAPI情報のURL
        private const val DIRECTIONS_URL = "https://maps.googleapis.com/maps/api/directions/json?language=ja"
        //DirectionAPIにアクセスするためのAPIキー
        private const val API_ID = "AIzaSyA95wBqtUdXY0bBJfwRg_Y6zCOHDVPECnI"
    }

    //メディアプレーヤープロパティ
    private var _player: MediaPlayer? = null
    private var flag = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etOrigin = findViewById<EditText>(R.id.etOrigin)
        val etDestination =  findViewById<EditText>(R.id.etDestination)
        val etO_txt = etOrigin.text
        val etD_txt = etDestination.text
        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            if (etO_txt.toString() != "" && etD_txt.toString() != "") {
                val urlFull =
                    "$DIRECTIONS_URL&origin=${etO_txt.toString()}&destination=${etD_txt.toString()}&mode=walking&key=$API_ID"
                receiveDirection(urlFull)
                Handler(Looper.getMainLooper()).postDelayed({
                    val durationText = findViewById<TextView>(R.id.durationDesc)
                    val num = durationText.text.toString()
                    //プロパティのメディアプレーヤーオブジェクトを生成
                    _player = MediaPlayer()
                    //所要時間に合った音楽を提示
                    //以下の音源は
                    // 「クラシック名曲サウンドライブラリー」(URL：http://classical-sound.seesaa.net/)
                    var no: Int = when (num) {
                        //"所要時間：〇〇分" -> R.raw."mp3ファイル名"
                        //例：銀座駅 - 日比谷駅
                        "所要時間：4分" -> R.raw.rossini_william_tell_overture_2020_ar
                        else -> 0
                    }
                    //音声ファイルのURI文字列を生成
                    val mediaFileUriStr = "android.resource://${packageName}/${no}"
                    //音声ファイルのURI文字列を元にURIオブジェクトを生成
                    val mediaFileUri = Uri.parse(mediaFileUriStr)
                    //プロパティのプレーヤーがnullでなければ
                    _player?.let {
                        //メディアプレーヤーに音声ファイルを指定
                        it.setDataSource(this@MainActivity, mediaFileUri)
                        //非同期でのメディア再生準備が完了した際のリスナを設定
                        it.setOnPreparedListener(PlayPreparedListener())
                        //メディア再生が終了した際のリスナを設定
                        it.setOnCompletionListener(PlayerCompletionListener())
                        //非同期でメディア再生を準備
                        it.prepareAsync()
                    }
                    //提示された所要時間に見合う曲がない場合
                    if (no == 0) {
                        Toast.makeText(this, R.string.toast, Toast.LENGTH_LONG).show()
                        flag = false
                    }else {
                        flag = true
                    }
                },2000)
            }
        }
    }

    //Direction情報の取得処理を行うメソッド
    @UiThread
    private fun receiveDirection(urlFull: String){
        //非同期処理をUIスレッドに戻すHandler
        val handler = HandlerCompat.createAsync(mainLooper)
        //非同期でDirection情報を取得する処理を記述する
        val backgroundReceiver = DirectionSearchBackgroundReceiver(handler, urlFull)
        val executeService = Executors.newSingleThreadExecutor()
        executeService.submit(backgroundReceiver)
    }

    //非同期でDirectionAPIにアクセスするためのクラス
    private inner class DirectionSearchBackgroundReceiver(handler: Handler, url: String): Runnable {
        //ハンドラオブジェクト
        private val _handler = handler
        //Direction情報を取得するURL
        private  val _url = url

        @WorkerThread
        override fun run() {
            //インターネットに接続する処理を記述
            //DirectionAPIから取得したJSON文字列。API情報が格納されている
            var result = ""
            //URLオブジェクトを生成
            val url = URL(_url)
            //URLオブジェクトからHttpURLConnectionオブジェクトを取得
            val con = url.openConnection() as? HttpURLConnection
            //conがnullじゃないならば
            con?.let {
                try {
                    //接続に使ってもよい時間を設定
                    it.connectTimeout = 1000
                    //データ取得に使ってもよい時間
                    it.readTimeout = 1000
                    //HTTP接続メソッドをGETに設定
                    it.requestMethod = "GET"
                    //接続
                    it.connect()
                    //HttpURLConnectionオブジェクトからレスポンスデータを取得
                    val stream = it.inputStream
                    //レスポンスデータであるInputStreamを文字列に変換
                    result = is2String(stream)
                    //InputStreamオブジェクトを解放
                    stream.close()
                }
                catch (ex: SocketTimeoutException){
                    Log.w(DEBUG_TAG, "通信タイムアウト", ex)
                }
                //HttpURLConnectionオブジェクトを解放
                it.disconnect()
            }

            //Web APIにアクセスするコードを記述
            val postExecutor = DirectionInfoPostExecutor(result)
            _handler.post(postExecutor)
        }

        //InputStreamをStringに変換
        private fun is2String(stream: InputStream): String {
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
            var line = reader.readLine()
            while (line != null){
                sb.append(line)
                line = reader.readLine()
            }
            reader.close()
            return sb.toString()
        }
    }

    //非同期でDirectionAPIを取得した後にUIスレッドでその情報を表示するためのクラス
    private inner class DirectionInfoPostExecutor(result: String): Runnable {
        //取得したDirection情報JSON文字列
        private val _result = result

        @UiThread
        override fun run() {
            //UIスレッドで行う処理コードを記述
            //ルートJSONオブジェクトを生成
            val rootJSON = JSONObject(_result)
            //routes情報JSON配列オブジェクトを取得
            val routesJSONArray = rootJSON.getJSONArray("routes")
            //routes情報JSONオブジェクトを取得
            val routesJSON = routesJSONArray.getJSONObject(0)
            //legs情報JSON配列オブジェクトを取得
            val legsJSONArray = routesJSON.getJSONArray("legs")
            //legs情報JSONオブジェクトを取得
            val legsJSON = legsJSONArray.getJSONObject(0)
            //distanceJSONオブジェクトを取得
            val distanceJSON = legsJSON.getJSONObject("distance")
            //distance_text文字列を取得
            val distanceText = distanceJSON.getString("text")
            //durationJSONオブジェクトを取得
            val durationJSON = legsJSON.getJSONObject("duration")
            //duration_text文字列を取得
            val durationText = durationJSON.getString("text")
            //画面に表示する「距離：○○」文字列を生成
            val distanceStr = "距離：${distanceText}"
            //画面に表示する「所要時間：○○」文字列を生成
            val durationStr = "所要時間：${durationText}"
            //Direction情報を表示するTextViewを取得
            val distanceDesc = findViewById<TextView>(R.id.distanceDesc)
            val durationDesc = findViewById<TextView>(R.id.durationDesc)
            //Direction情報を表示
            distanceDesc.text = distanceStr
            durationDesc.text = durationStr
        }
    }

    //プレーヤーの再生準備が整ったときのリスナクラス
    private inner class PlayPreparedListener : MediaPlayer.OnPreparedListener {
        override fun onPrepared(mp: MediaPlayer){
            //各ボタンをタップ可能に設定
            val btPlay = findViewById<Button>(R.id.btPlay)
            btPlay.isEnabled = true
            btPlay.isVisible = true
        }
    }

    //再生が終了した時のリスナクラス
    private inner class  PlayerCompletionListener : MediaPlayer.OnCompletionListener {
        override fun onCompletion(mp: MediaPlayer) {
            //再生ボタンのラベルを「再生」に設定
            val btPlay = findViewById<Button>(R.id.btPlay)
            btPlay.setText(R.string.bt_play_play)
        }
    }

    //再生ボタンタップ時の処理
    fun onPlayButtonClick(view: View){
        //プロパティのプレーヤーがnullじゃなかったら
        _player?.let {
            //再生ボタンを取得
            val btPlay = findViewById<Button>(R.id.btPlay)
            //プレーヤーが再生中ならば
            if (it.isPlaying){
                //プレーヤーを一時停止
                it.pause()
                //再生ボタンのラベルを「再生」に設定
                btPlay.setText(R.string.bt_play_play)
            }
            //プレーヤーが再生中でなければ
            else{
                //プレーヤーを再生
                it.start()
                //再生ボタンのラベルを「一時停止」に設定
                btPlay.setText(R.string.bt_play_pause)
            }
        }
        if (flag == false){
            Toast.makeText(this, R.string.toast, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        //プロパティのプレーヤーがnullじゃなかったら
        _player?.let {
            //プレーヤーが再生中なら
            if (it.isPlaying){
                //プレーヤーを停止
                it.stop()
            }
            //プレーヤーを解放
            it.release()
        }
        //プレーヤー用のプロパティをnullに
        _player = null
        //親クラスのメソッド呼び出し
        super.onDestroy()
    }
}