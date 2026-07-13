package com.ljm.audiotoollib.upnpserver.httpserver;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.ljm.audiotoollib.upnpserver.entity.PlayStatusBean;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;
import com.ljm.audiotoollib.upnpserver.entity.SlaveBean;
import com.ljm.audiotoollib.upnpserver.listener.OnUpnpHttpServerListener;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class UpnpHttpServer extends NanoHTTPD implements IResourceServer {

    private static final String TAG = "NanoHttpServer";
    private static final Map<String, String> MIME_TYPE = new HashMap<>();
    private static final String MIME_PLAINTEXT = "text/plain";
    private static UpnpHttpServer instance;
    private OnUpnpHttpServerListener mListener;
    private final Gson gson = new Gson();
    private static int mPort = SWDeviceStatus.AND_HARDWARE_PORT;

    public static UpnpHttpServer getInstance(int port) {
        if (instance == null) {
            mPort = port;
            instance = new UpnpHttpServer(port);
        }
        return instance;
    }


    public static UpnpHttpServer getInstance() {
        return getInstance(mPort);
    }

    static {
        MIME_TYPE.put("jpg", "image/*");
        MIME_TYPE.put("jpeg", "image/*");
        MIME_TYPE.put("png", "image/*");
        MIME_TYPE.put("mp3", "audio/*");
        MIME_TYPE.put("mp4", "video/*");
        MIME_TYPE.put("wav", "video/*");
    }

    public UpnpHttpServer(int port) {
        super(port);
    }

    @Override
    public Response serve(IHTTPSession session) {

//        Log.i(TAG,"serve uri: " + session.getUri());
//        Log.i(TAG,"serve header: " + session.getHeaders().toString());
//        Log.i(TAG,"serve params: " + session.getParms().toString());

        return dealWith(session);
    }


    private Response dealWith(IHTTPSession session) {
        Date dateTime = new Date();
        if (Method.POST == session.getMethod()) {
            //获取请求头数据
            Map<String, String> header = session.getHeaders();
            //获取传参参数
            Map<String, String> params = new HashMap<String, String>();
            try {
                session.parseBody(params);
                String paramStr = params.get("postData");
                if (TextUtils.isEmpty(paramStr)) {
                    return newFixedLengthResponse("success");
                }
                paramStr = paramStr.replace("\r\n", " ");
//                JSONObject jsonParam = JSON.parseObject(paramStr);
                Map<String, Object> result = new HashMap<>();

                return newFixedLengthResponse("success");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ResponseException e) {
                e.printStackTrace();
            }
            return newFixedLengthResponse("success");
        } else if (Method.GET == session.getMethod()) {

            Map<String, List<String>> parameters = session.getParameters();
            List<String> cmdList = parameters.get("command");
            String response = "success";
            if (cmdList.size() == 0) return newFixedLengthResponse("404");

            String param = cmdList.get(0);

//            Log.i(TAG, "get param: " + param);

            if (param.equals("getStatusEx")) {
                SWDeviceStatus status = new SWDeviceStatus();
                response = gson.toJson(status);
            } else if (param.equals("getPlayerStatus")) {
                PlayStatusBean playStatusBean = new PlayStatusBean();
                if (mListener != null) {
                    playStatusBean = mListener.getPlayerStatus();
                }
                response = gson.toJson(playStatusBean, PlayStatusBean.class);
//                Log.i(TAG, "get getPlayerStatus response: " + response);
            } else if (param.contains("setPlayerCmd:play:")) {
                String url = param.replace("setPlayerCmd:play:", "");
                if (mListener != null) {
                    mListener.setPlayerCmdPlay(url);
                }
            } else if (param.contains("multiroom:getSlaveList")) {
                SlaveBean slaveBean = new SlaveBean();
                response = gson.toJson(slaveBean, SlaveBean.class);
            } else if (param.contains("setPlayerCmd:slave_vol:")) {
                int vol = 50;
                try {
                    vol = Integer.parseInt(param.replace("setPlayerCmd:slave_vol:",""));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(mListener != null) {
                    mListener.setPlayerCmdSlaveVol(vol);
                }
            } else if (param.contains("restoreToDefault")) {
                Log.i(TAG, "restoreToDefault");
                if(mListener != null) {
                    mListener.restoreToDefault();
                }
            }  else {
                Log.i(TAG, "other param: " + param);
            }


            return newFixedLengthResponse(response);
        }

        return newFixedLengthResponse("404");
    }

    public static Response newFixedLengthResponse(String msg) {
        return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_HTML, msg);
    }

    @Override
    public void startServer() {
        try {
            Log.i(TAG, "startServer");
            start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopServer() {
        stop();
    }

    public void setGetPlayerStatusListener(OnUpnpHttpServerListener listener) {
        mListener = listener;
    }
}
