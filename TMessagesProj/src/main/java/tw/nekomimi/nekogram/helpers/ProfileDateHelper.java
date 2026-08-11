package tw.nekomimi.nekogram.helpers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;

public class ProfileDateHelper {
    private static final String JSON_FILE = "id_date.json";
    private static final ArrayList<ProfileDateData> profileDateDataList = new ArrayList<>();

    private static void loadData() {
        try {
            InputStream in = ApplicationLoader.applicationContext.getAssets().open(JSON_FILE);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[10240];
            int c;
            while ((c = in.read(buffer)) != -1) {
                bos.write(buffer, 0, c);
            }
            bos.close();
            in.close();
            String json = bos.toString("UTF-8");
            JSONObject object = new JSONObject(json);
            JSONArray data = object.getJSONArray("data");
            profileDateDataList.clear();
            for (int i = 0; i<data.length(); i++){
                JSONObject o = data.getJSONObject(i);
                profileDateDataList.add(new ProfileDateData(o.getLong("id"), o.getLong("date")));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String getUserTime(String prefix, long date) {
        String st = LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(new Date(date)), LocaleController.getInstance().getFormatterDay().format(new Date(date)));
        return prefix + " " + st;
    }

    public static String getUserTime(Long userId) {
        if (profileDateDataList.isEmpty()) {
            loadData();
        }
        if (profileDateDataList.isEmpty()) {
            return "unknown";
        }
        for (int i = 1; i < profileDateDataList.size(); i++){
            ProfileDateData data1 = profileDateDataList.get(i - 1);
            ProfileDateData data2 = profileDateDataList.get(i);
            if (userId >= data1.id() && userId <= data2.id()) {
                long idx = userId - data1.id();
                long idxRange = data2.id() - data1.id();
                double t = (double) idx / idxRange;
                long date1 = data1.date();
                long date2 = data2.date();
                double date = (date1 + t * (date2 - date1)) * 1000.0;
                long dateLong = Math.round(date);
                return getUserTime("~", dateLong);
            }
        }
        if (userId <= 1000000) {
            return getUserTime("=", 1380326400000L);
        }
        return getUserTime(">", 1711889200000L);
    }

    public record ProfileDateData(long id, long date) {
    }
}
