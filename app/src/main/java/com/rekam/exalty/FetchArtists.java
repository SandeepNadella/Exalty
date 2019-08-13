package com.rekam.exalty;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FetchArtists extends AsyncTask<String, Void, String> {

    private Context mainContext;

    FetchArtists(Context mainContext) {
        this.mainContext = mainContext;
    }

    protected String doInBackground(String... location) {
        String artist = "";
        try {
            URL url = new URL("http://34.92.8.123:80?city=" + location[0]);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                bufferedReader.close();
                artist = stringBuilder.toString();
                artist = artist.replaceAll("\n","");

            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //For weather
        try {
            URL url = new URL("http://34.92.8.123:80/weather?city="+location[0]);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                bufferedReader.close();
                String weather = stringBuilder.toString();
                weather = weather.replaceAll("\n", "");
                String[] weathers = weather.split(" ");
                if(weathers.length == 2){
                    weather = weathers[1];
                }
                else{
                    weather = weathers[0];
                }
                SharedPreferences preferences = this.mainContext.getSharedPreferences("ActionAppPreferences", 0);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("Weather", weather).apply();

            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return artist;
    }

    @Override
    protected void onPostExecute(String result) {
        SharedPreferences preferences = this.mainContext.getSharedPreferences("ActionAppPreferences", 0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("Artist", result).apply();
    }
}


