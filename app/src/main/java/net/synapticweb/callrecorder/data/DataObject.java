package net.synapticweb.callrecorder.data;

import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;
import org.json.JSONStringer;

public class DataObject {
    public JSONObject minutes;

    public JSONObject getMinutes() {
        return minutes;
    }

    public void setMinutes(JSONObject minutes) {
        this.minutes = minutes;
    }

    @Override
    public String toString() {
        return " {" +
                "minutes=" + minutes +
                '}';
    }
}