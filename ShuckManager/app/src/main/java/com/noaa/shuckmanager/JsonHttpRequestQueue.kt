package com.noaa.shuckmanager

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

// Queue of HTTP requests with JSON packet data
class JsonHttpRequestQueue() {
    val queue: Deque<HttpRequestTask> = ArrayDeque()
    var outstandingRequest: HttpRequestTask? = null

    // Add a request to the given URI containing the given data according to the given method (GET, POST, etc)
    fun addRequest(method: String, uri: String, data: JSONObject) {
        queue.addLast(HttpRequestTask(method, uri, data) {
            outstandingRequest = null
            checkDoRequest()
        })

        // see if we can do a request, and do it if so
        checkDoRequest()
    }

    // Check if there are any requests in flight; if not, send the next request in the queue
    fun checkDoRequest() {
        if (outstandingRequest == null && !queue.isEmpty()) {
            // no request in flight, do this one
            outstandingRequest = queue.removeFirst()

            outstandingRequest!!.execute()
        }
    }
}