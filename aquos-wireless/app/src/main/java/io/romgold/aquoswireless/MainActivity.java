package io.romgold.aquoswireless;
import android.app.Activity; import android.content.Intent; import android.net.wifi.WifiManager; import android.os.Bundle; import android.provider.Settings; import android.text.format.Formatter; import android.view.Gravity; import android.view.View; import android.widget.Button; import android.widget.LinearLayout; import android.widget.TextView; import android.graphics.Color; import android.graphics.Typeface;
public class MainActivity extends Activity {
 private LocalWebServer server; private TextView address;
 @Override public void onCreate(Bundle state) { super.onCreate(state); getWindow().getDecorView().setSystemUiVisibility(4|2|4096);
  LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setGravity(Gravity.CENTER); root.setPadding(48,32,48,32); root.setBackgroundColor(Color.rgb(8,17,35));
  TextView title=new TextView(this); title.setText("AQUOS WIRELESS HUB"); title.setTextColor(Color.WHITE); title.setTextSize(28); title.setTypeface(Typeface.DEFAULT,1); root.addView(title);
  TextView sub=new TextView(this); sub.setText("Управление телевизором и просмотр фото/видео с телефона по Wi‑Fi"); sub.setTextColor(Color.LTGRAY); sub.setTextSize(17); sub.setGravity(Gravity.CENTER); root.addView(sub);
  address=new TextView(this); address.setTextColor(Color.rgb(115,190,255)); address.setTextSize(24); address.setGravity(Gravity.CENTER); root.addView(address);
  Button access=new Button(this); access.setText("Включить управление с телефона"); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(access);
  TextView hint=new TextView(this); hint.setText("Откройте адрес в браузере телефона. Оба устройства должны быть в одной Wi‑Fi сети. Для навигации и ввода текста включите специальное разрешение."); hint.setTextColor(Color.LTGRAY); hint.setTextSize(15); hint.setGravity(Gravity.CENTER); root.addView(hint); setContentView(root); startServer();
 }
 private void startServer(){try{server=new LocalWebServer(this);server.start();WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);address.setText("http://"+Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress())+":8765");}catch(Exception e){address.setText("Ошибка запуска: "+e.getMessage());}}
 @Override protected void onDestroy(){if(server!=null)server.stop();super.onDestroy();}
}
