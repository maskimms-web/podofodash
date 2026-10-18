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

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(18,14,18,12); root.setBackgroundColor(Color.rgb(14,21,30));
        TextView title = new TextView(this); title.setText("GOLF CAN DIAGNOSTIC v0.5"); title.setTextColor(Color.WHITE); title.setTextSize(22); root.addView(title);
        TextView sub = new TextView(this); sub.setText("Без root • тільки читання APK • Android SDK " + Build.VERSION.SDK_INT); sub.setTextColor(Color.LTGRAY); root.addView(sub);
        addButton(root,"ЗНАЙТИ ШЛЯХИ APK", new View.OnClickListener(){public void onClick(View v){ new Thread(new Runnable(){public void run(){inspect(false);}}).start();}});
        addButton(root,"ВИТЯГНУТИ CANBUS APK",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){inspect(true);}}).start();}});
        addButton(root,"1. ВИБРАТИ ПАПКУ USB",new View.OnClickListener(){public void onClick(View v){chooseUsb();}});
        addButton(root,"2. ЗБЕРЕГТИ ЗВІТ І APK НА USB",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){saveToUsb();}}).start();}});
        logView = new TextView(this); logView.setTextColor(Color.rgb(196,236,208)); logView.setTextSize(13); logView.setPadding(6,8,6,8); logView.setMovementMethod(new ScrollingMovementMethod());
        ScrollView sc = new ScrollView(this); sc.addView(logView); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        report = new File(getExternalFilesDir(null),"GolfCanDiagnostic-v0.5-report.txt");
        String saved=getPreferences(0).getString("usb_tree",null); if(saved!=null){usbTree=Uri.parse(saved);}
        log("Старт. Копіювання APK тільки для читання. Ніяких CAN-команд.");
        log("Папка копій: " + getExternalFilesDir(null));
    }
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
