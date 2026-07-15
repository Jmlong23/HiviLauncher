package com.hivi.launcher.utils.network;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface ApiService {
    @Headers("Cache-Control: no-cache")
    @GET("user/getQr")
    Observable<String> getQr();

    @Headers("Cache-Control: no-cache")
    @GET("user/getQr")
    Observable<String> getQr(@Query("id") String id);

    @GET("user/qrLogout")
    Observable<String> qrLogout(@Query("facilityCode") String facilityCode);
}
