package com.hivi.launcher.utils.network;

import io.reactivex.Observable;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
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

    @Headers("Cache-Control: no-cache")
    @GET("user/details")
    Observable<String> getUserDetails();

    @Headers("Cache-Control: no-cache")
    @GET("version/details")
    Observable<String> getAppVersionDetails(@Query("type") String type);

    /**
     * Upload a device diagnostic log archive. This matches the logger endpoint used by HiviAudio.
     */
    @Multipart
    @POST("file/upload/logger")
    Observable<String> uploadLogger(
            @Part MultipartBody.Part file,
            @Part("uuid") RequestBody uuid,
            @Part("appVersion") RequestBody appVersion,
            @Part("speakerDeviceInfo") RequestBody speakerDeviceInfo,
            @Part("userId") RequestBody userId,
            @Part("userPhone") RequestBody userPhone);
}
