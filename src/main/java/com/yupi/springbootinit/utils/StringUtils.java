package com.yupi.springbootinit.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;

import java.io.IOException;

public class StringUtils {
    /**
     * 校验AI生成的Json代码是否正确
     * @param json
     * @return
     */
    public static boolean isValidJson(String json){
        if(org.apache.commons.lang3.StringUtils.isBlank(json)){
            return false;
        }
        TypeAdapter<JsonElement> adapter = new Gson().getAdapter(JsonElement.class);
        try {
            adapter.fromJson(json);
        } catch (JsonSyntaxException | IOException e) {
            return false;
        }
        return true;
    }
}
