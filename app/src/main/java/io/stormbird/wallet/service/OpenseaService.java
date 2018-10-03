package io.stormbird.wallet.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.util.Log;

import com.caverock.androidsvg.SVG;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.stormbird.wallet.entity.DownloadLink;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Created by James on 2/10/2018.
 * Stormbird in Singapore
 */

public class OpenseaService
{
    private static OkHttpClient httpClient;
    private static Map<String, Long> balanceAccess = new ConcurrentHashMap<>();
    private Context context;

    //TODO: remove old files not accessed for some time
    //      On service creation, check files for old files and delete

    public OpenseaService(Context ctx)
    {
        context = ctx;
        balanceAccess.clear();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /*
    https://api.opensea.io/api/v1/assets/?owner=0xbc8dAfeacA658Ae0857C80D8Aa6dE4D487577c63&order_by=current_price&order_direction=asc
     */
    public Single<JSONObject> queryBalance(String address)
    {
        return Single.fromCallable(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("https://api.opensea.io/api/v1/assets/?owner=");
            sb.append(address);
            sb.append("&order_by=current_price&order_direction=asc");
            JSONObject result = null;

            try
            {
                if (balanceAccess.containsKey(address))
                {
                    long lastAccess = balanceAccess.get(address);
                    if (lastAccess > 0 && (System.currentTimeMillis() - lastAccess) < 1000 * 30)
                    {
                        Log.d("OPENSEA", "Polling Opensea very frequently: " + (System.currentTimeMillis() - lastAccess));
                    }
                }

                Request request = new Request.Builder()
                        .url(sb.toString())
                        .get()
                        .build();

                okhttp3.Response response = httpClient.newCall(request).execute();
                String jsonResult = response.body().string();
                balanceAccess.put(address, System.currentTimeMillis());

                if (jsonResult != null && jsonResult.length() > 10)
                {
                    result = new JSONObject(jsonResult);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            return result;
        });
    }

    private static Bitmap getBitmap(SVG svg, int width)
    {
        float aspectRatio = svg.getDocumentAspectRatio();
        int height = (int)((float)width / aspectRatio);
        Bitmap bitmap = Bitmap.createBitmap(width,
                                            height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // Clear background to white
        canvas.drawRGB(255, 255, 255);
        svg.renderToCanvas(canvas);
        return bitmap;
    }

    public Single<Bitmap> fetchBitmap(String strURL, int width)
    {
        return Single.fromCallable(() -> {
            File bitmapFile = getLocalFile(strURL);
            if (!bitmapFile.exists())
            {
                fetchFile(strURL).blockingGet();
                if (!bitmapFile.exists()) return Bitmap.createBitmap(5, 5, Bitmap.Config.ALPHA_8);
            }

            System.out.println(bitmapFile.length());
            FileInputStream fis = new FileInputStream(bitmapFile);
            SVG svg = SVG.getFromInputStream(fis);

            //convert file to bitmap
            Bitmap convert = getBitmap(svg, width);

            return convert;
        });
    }

    public Single<String> fetchFile(String strURL)
    {
        return Single.fromCallable(() -> {
            byte[] largebuffer = new byte[65536];
            String errorFile = "error";
            String returnValue = errorFile;
            File targetFile;
            try
            {
                targetFile = getLocalFile(strURL);
                //already have it locally?
                if (targetFile.exists()) return targetFile.getName();

                DownloadLink dl = obtainFileConnection(strURL);

                if (dl == null) return errorFile;

                FileOutputStream fos = new FileOutputStream(targetFile);
                OutputStream os = new BufferedOutputStream(fos);

                int bufferLength;
                InputStream in = new BufferedInputStream(dl.fileURL.openStream());

                //now, read through the input buffer and write the contents to the file
                while ((bufferLength = in.read(largebuffer)) > 0)
                {
                    //add the data in the buffer to the file in the file output stream (the file on the sd card
                    os.write(largebuffer, 0, bufferLength);
                }

                returnValue = targetFile.getName();

                os.close();
                fos.close();
                in.close();
            }
            catch (MalformedURLException e)
            {
                returnValue = errorFile;
                e.printStackTrace();
            }
            catch (IOException e)
            {
                returnValue = errorFile;
                e.printStackTrace();
            }
            catch (Exception e)
            {
                returnValue = errorFile;
                e.printStackTrace();
            }

            return returnValue;
        });
    }

    private DownloadLink obtainFileConnection(String strURL) throws IOException
    {
        URL fileAddr = new URL(strURL);
        //create the new connection
        HttpURLConnection urlConnection = (HttpURLConnection) fileAddr.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setDoOutput(true);
        urlConnection.connect();

        int totalSize;
        totalSize = urlConnection.getContentLength();

        if (totalSize <= 0) return null;

        DownloadLink dl = new DownloadLink();
        dl.totalSize = totalSize;
        dl.fileURL = fileAddr;

        return dl;
    }

    private File getLocalFile(String strURL)
    {
        int slashIndex = strURL.lastIndexOf('/');
        int secondSlashIndex = strURL.lastIndexOf('/', slashIndex - 1);
        if (secondSlashIndex < 0) secondSlashIndex = slashIndex;
        String name = "_cached_" + strURL.substring(secondSlashIndex + 1);

        name = name.replace('/', '-');

        File file = new File(context.getFilesDir(), name);

        //touch the file
        if (file.exists())
        {
            file.setLastModified(System.currentTimeMillis());
        }

        //in this case, going to save it in the private data directory of the app
        return file;
    }
}
