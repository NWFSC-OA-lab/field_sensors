package com.noaa.shuckmanager

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class JsonHttpRequestQueue() {
    val queue: Deque<HttpRequestTask> = ArrayDeque()
    var outstandingRequest: HttpRequestTask? = null

    fun addRequest(method: String, uri: String, data: JSONObject) {
        queue.addLast(HttpRequestTask(method, uri, data) {
            outstandingRequest = null
            checkDoRequest()
        })

        // see if we can do a request, and do it if so
        checkDoRequest()
    }

    fun checkDoRequest() {
        if (outstandingRequest == null && !queue.isEmpty()) {
            // no request in flight, do this one
            outstandingRequest = queue.removeFirst()

            outstandingRequest!!.execute()
        }
    }
}