package com.golf.candiagnostic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.text.method.ScrollingMovementMethod;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {
    private TextView logView;
    private final StringBuilder logs = new StringBuilder();
    private static final int PICK_USB = 41;
    private Uri usbTree;
    private final String[] packages = {"com.xygala.canbus", "com.xygala.pvcanset"};
    private final Map<String,File> exported = Collections.synchronizedMap(new LinkedHashMap<String,File>());
    private final Object lock = new Object();
    private File report;
    private BroadcastReceiver receiver;
    private int eventCount=0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(18,14,18,12); root.setBackgroundColor(Color.rgb(14,21,30));
        TextView title = new TextView(this); title.setText("GOLF CAN DIAGNOSTIC v0.6"); title.setTextColor(Color.WHITE); title.setTextSize(22); root.addView(title);
        TextView sub = new TextView(this); sub.setText("Без root • тільки читання APK • Android SDK " + Build.VERSION.SDK_INT); sub.setTextColor(Color.LTGRAY); root.addView(sub);
        addButton(root,"СЛУХАТИ CAN ПОДІЇ",new View.OnClickListener(){public void onClick(View v){startListening();}});
        addButton(root,"ЗУПИНИТИ СЛУХАННЯ",new View.OnClickListener(){public void onClick(View v){stopListening();}});
        addButton(root,"ПОЗНАЧКА: ДВЕРІ ВІДКРИТО",new View.OnClickListener(){public void onClick(View v){log("РУЧНА ПОЗНАЧКА: двері відкрито (НЕ CAN-подія)");}});
        addButton(root,"ПОЗНАЧКА: ДВЕРІ ЗАКРИТО",new View.OnClickListener(){public void onClick(View v){log("РУЧНА ПОЗНАЧКА: двері закрито (НЕ CAN-подія)");}});
        addButton(root,"ЗНАЙТИ ШЛЯХИ APK", new View.OnClickListener(){public void onClick(View v){ new Thread(new Runnable(){public void run(){inspect(false);}}).start();}});
        addButton(root,"ВИТЯГНУТИ CANBUS APK",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){inspect(true);}}).start();}});
        addButton(root,"1. ВИБРАТИ ПАПКУ USB",new View.OnClickListener(){public void onClick(View v){chooseUsb();}});
        addButton(root,"2. ЗБЕРЕГТИ ЗВІТ І APK НА USB",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){saveToUsb();}}).start();}});
        logView = new TextView(this); logView.setTextColor(Color.rgb(196,236,208)); logView.setTextSize(13); logView.setPadding(6,8,6,8); logView.setMovementMethod(new ScrollingMovementMethod());
        ScrollView sc = new ScrollView(this); sc.addView(logView); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        report = new File(getExternalFilesDir(null),"GolfCanDiagnostic-v0.6-report.txt");
        String saved=getPreferences(0).getString("usb_tree",null); if(saved!=null){usbTree=Uri.parse(saved);}
        log("Старт. Копіювання APK тільки для читання. Ніяких CAN-команд.");
        log("Папка копій: " + getExternalFilesDir(null));
    }
    private void startListening(){
        if(receiver!=null){log("Прослуховування вже працює");return;}
        final String[] actions={"CANBUS_BROADCAST","ACTION_CANBUS_RX_MSG","GOLF_DOOR_STATE",
            "com.xygala.canbus.CANBUS_BROADCAST","com.xygala.canbus.ACTION_CANBUS_RX_MSG",
            "com.xygala.canbus.GOLF_DOOR_STATE"};
        IntentFilter filter=new IntentFilter();for(String a:actions)filter.addAction(a);
        receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
            eventCount++;StringBuilder sb=new StringBuilder("ПОДІЯ #"+eventCount+" action="+intent.getAction());
            Bundle b=intent.getExtras();if(b!=null){for(String key:b.keySet()){
                try{Object val=b.get(key);String desc=val==null?"null":val.getClass().getSimpleName();
                    if(val instanceof byte[]){byte[] a=(byte[])val;StringBuilder hex=new StringBuilder();for(int i=0;i<Math.min(a.length,64);i++)hex.append(String.format(Locale.US,"%02X ",a[i]&255));desc+=" len="+a.length+" hex="+hex;}
                    else if(val instanceof int[])desc+=" "+Arrays.toString((int[])val);
                    else if(val instanceof long[])desc+=" "+Arrays.toString((long[])val);
                    else if(val instanceof String || val instanceof Number || val instanceof Boolean)desc+=" "+val;
                    sb.append(" | ").append(key).append("=").append(desc);
                }catch(Exception e){sb.append(" | ").append(key).append("=<error>");}
            }}log(sb.toString());
        }};
        try{registerReceiver(receiver,filter);log("СЛУХАЄМО "+actions.length+" кандидатів. Відсутність подій НЕ доводить відсутність CAN.");}
        catch(Exception e){receiver=null;log("ПОМИЛКА приймача: "+e);}
    }
    private void stopListening(){if(receiver!=null){try{unregisterReceiver(receiver);}catch(Exception ignored){}receiver=null;log("Прослуховування зупинено. Подій: "+eventCount);}}
    @Override protected void onDestroy(){stopListening();super.onDestroy();}
    private void addButton(LinearLayout root,String text,View.OnClickListener click){Button b=new Button(this);b.setText(text);root.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(click);}
    private void log(String s){final String line="["+new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"] "+s+"\n";synchronized(lock){logs.append(line); if(report!=null){try(FileOutputStream f=new FileOutputStream(report,true)){f.write(line.getBytes("UTF-8"));}catch(Exception ignored){}}}runOnUiThread(new Runnable(){public void run(){logView.append(line);}});}
    private void inspect(boolean copy){
        for(String pkg:packages){
            log("--- " + pkg + " ---");
            try{
                PackageInfo info=getPackageManager().getPackageInfo(pkg,0);
                ApplicationInfo ai=info.applicationInfo;
                log("version="+info.versionName+" sourceDir="+ai.sourceDir);
                log("publicSourceDir="+ai.publicSourceDir+" splitCount="+(ai.splitSourceDirs==null?0:ai.splitSourceDirs.length));
                if(copy){copyOne(pkg,"base",ai.sourceDir);if(ai.splitSourceDirs!=null){for(int i=0;i<ai.splitSourceDirs.length;i++)copyOne(pkg,"split"+i,ai.splitSourceDirs[i]);}}
            }catch(Exception e){log("ПОМИЛКА пакета: "+e.getClass().getSimpleName()+": "+e.getMessage());}
        }
        log(copy?"Копіювання завершено. Збережи файли на USB.":"Пошук шляхів завершено.");
        if(copy && usbTree!=null)saveToUsb();
    }
    private void copyOne(String pkg,String suffix,String path){
        File source=new File(path);
        log("Спроба читання: "+path+" exists="+source.exists()+" readable="+source.canRead()+" bytes="+source.length());
        File target=new File(getExternalFilesDir(null),pkg+"-"+suffix+".apk");
        long total=0;
        try(InputStream in=new BufferedInputStream(new FileInputStream(source));OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
            byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);total+=n;}
        }catch(Exception e){target.delete();log("ПОМИЛКА копіювання: "+e.getClass().getSimpleName()+": "+e.getMessage());return;}
        if(total<=0 || target.length()!=total){target.delete();log("ПОМИЛКА: копія порожня або неповна");return;}
        exported.put(target.getName(),target);log("УСПІХ: "+target.getName()+" bytes="+total);
    }
    private void chooseUsb(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(intent,PICK_USB);}catch(Exception e){log("Не вдалося відкрити вибір USB: "+e);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_USB||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri,flags);
            usbTree=uri;getPreferences(0).edit().putString("usb_tree",uri.toString()).apply();
            log("Папку вибрано: "+uri+". Переконайся, що це USB, а не внутрішня пам'ять.");
        }catch(Exception e){log("Помилка доступу до USB: "+e);}
    }
    private void saveToUsb(){
        Uri tree=usbTree;
        if(tree==null){log("Спочатку натисни ВИБРАТИ ПАПКУ USB");return;}
        try{
            String parent=DocumentsContract.getTreeDocumentId(tree);
            Uri dir=DocumentsContract.buildDocumentUriUsingTree(tree,parent);
            ArrayList<File> files=new ArrayList<File>();
            synchronized(exported){files.addAll(exported.values());}
            // Report is copied last so it includes export progress messages.
            for(File f:files)writeUsbFile(dir,f,"application/vnd.android.package-archive");
            log("Збереження звіту на вибраний USB-носій...");
            writeUsbFile(dir,report,"text/plain");
        }catch(Exception e){log("ПОМИЛКА USB: "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void writeUsbFile(Uri dir,File src,String mime)throws Exception{
        if(src==null||!src.isFile()||src.length()==0){log("Пропущено порожній файл: "+src);return;}
        Uri target=DocumentsContract.createDocument(getContentResolver(),dir,mime,src.getName());
        if(target==null)throw new IOException("Не створено файл "+src.getName());
        long total=0;
        try(InputStream in=new BufferedInputStream(new FileInputStream(src));OutputStream out=getContentResolver().openOutputStream(target,"w")){
            if(out==null)throw new IOException("Немає потоку запису");
            byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1){out.write(buf,0,n);total+=n;}
            out.flush();
        }catch(Exception e){try{DocumentsContract.deleteDocument(getContentResolver(),target);}catch(Exception ignored){}throw e;}
        log("USB: записано "+src.getName()+" ("+total+" байт)");
    }
}
