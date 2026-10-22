package mk.td.archive.document;

import org.json.*;
import java.util.*;

/** Stable keys preserve values when a display label is renamed. */
public final class CardFields {
    public static class Field {public final String id,label;Field(String id,String label){this.id=id;this.label=label;}}
    public static List<Field> list(Store.Record record){
        List<Field> out=new ArrayList<>();
        try{JSONArray fields=new JSONArray(record.fields);for(int i=0;i<fields.length();i++){JSONObject x=fields.getJSONObject(i);out.add(new Field(x.getString("id"),x.getString("label")));}}catch(JSONException e){throw new IllegalStateException("Невалидни полиња.",e);}return out;
    }
    public static String value(Store.Asset asset,Field field){try{return new JSONObject(asset.fields).optString(field.id,"");}catch(JSONException e){throw new IllegalStateException("Невалидни податоци.",e);}}
    public static void add(Store store,Store.Record record,String label) throws JSONException {
        label=label.trim();if(label.isEmpty())throw new IllegalArgumentException("Внеси име на полето.");if(label.length()>80)throw new IllegalArgumentException("Името на полето може да има до 80 знаци.");
        for(Field f:list(record))if(f.label.equalsIgnoreCase(label))throw new IllegalArgumentException("Веќе постои поле со тоа име.");
        JSONArray fields=new JSONArray(record.fields);JSONObject x=new JSONObject();x.put("id",UUID.randomUUID().toString());x.put("label",label);fields.put(x);record.fields=fields.toString();store.save(record);
    }
    public static void rename(Store store,Store.Record record,Field field,String label) throws JSONException {
        label=label.trim();if(label.isEmpty())throw new IllegalArgumentException("Внеси име на полето.");if(label.length()>80)throw new IllegalArgumentException("Името на полето може да има до 80 знаци.");
        for(Field f:list(record))if(!f.id.equals(field.id)&&f.label.equalsIgnoreCase(label))throw new IllegalArgumentException("Веќе постои поле со тоа име.");
        JSONArray fields=new JSONArray(record.fields);for(int i=0;i<fields.length();i++)if(fields.getJSONObject(i).getString("id").equals(field.id))fields.getJSONObject(i).put("label",label);
        record.fields=fields.toString();store.save(record);
    }
}
