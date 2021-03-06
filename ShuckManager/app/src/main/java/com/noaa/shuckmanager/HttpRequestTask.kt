package com.noaa.shuckmanager

import android.os.AsyncTask
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

// Asynchronous task sending JSON packets to the given URI according to the given method
class HttpRequestTask (
    val method: String,
    val uri: String,
    val jsonData: JSONObject,
    val onResult: () -> Unit
): AsyncTask<String, Unit, String?>() {
    var response: String = ""

    override fun doInBackground(vararg params: String): String? {
        try {
            val url = URL(uri);
            val conn = url.openConnection() as HttpURLConnection

            // Request header
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json");
            conn.doInput = true

            Log.i("GetRequest", "Packet: ${jsonData}")

            // Write JSON data to the request body
            conn.outputStream.use {
                it.write(jsonData.toString().toByteArray())
                it.flush()
            }

            // Waits until a response code is received
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                // Read response data
                response = readStream(conn.inputStream)
                Log.i("GetRequest", "OK: ${uri}")
                return response
            }
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    // Called after response is received, or on failure
    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)

        onResult()
        if (result != null) {
            Log.i("GetRequest", "Response: $result")
        }
    }

    private fun writeStream(output: OutputStream, data: String) {
        output.use {
            it.write(data.toByteArray())
        }
    }

    // Reads from the given input stream into a string
    private fun readStream(input: InputStream): String {
        var reader: BufferedReader? = null
        var response = StringBuffer()

        try {
            reader = BufferedReader(InputStreamReader(input))
            var line: String? = reader.readLine()
            while (line != null) {
                response.append(line)
                line = reader.readLine()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return response.toString()
    }
}