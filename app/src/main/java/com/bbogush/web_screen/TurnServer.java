package com.bbogush.web_screen;

import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.Call;

public interface TurnServer {
    @PUT("/_turn/<xyrsys_channel>")
    Call<TurnServerPojo> getIceCandidates(@Header("Authorization") String authkey);
}