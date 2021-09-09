package net.runelite.cache.definitions.loaders;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.runelite.http.api.RuneLiteAPI;
import net.runelite.cache.definitions.IconMapDefinition;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class IconMaperLoader {

    private List<IconMapDefinition> iconMapping;

    public IconMaperLoader(){
        this.iconMapping = null;
    }

    public void load() throws IOException
    {
        HttpUrl url = HttpUrl.parse("https://pastebin.com/raw/ny2CQ9pU");

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = RuneLiteAPI.CLIENT.newCall(request).execute())
        {
            InputStream in = response.body().byteStream();
            // CHECKSTYLE:OFF
            this.iconMapping = RuneLiteAPI.GSON.fromJson(new InputStreamReader(in), new TypeToken<List<IconMapDefinition>>() { }.getType());
            // CHECKSTYLE:ON
        }
        catch (JsonParseException ex)
        {
            throw new IOException(ex);
        }
    }

    public IconMapDefinition getIconByID(int iconId){
        if(this.iconMapping == null){
            return null;
        }
        for (IconMapDefinition iconMapD : this.iconMapping) {
            if (iconMapD.getId() == iconId) {
                return iconMapD;
            }
        }
        return null;
    }
}