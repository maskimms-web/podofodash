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
import android.os.IBinder;
import android.os.Binder;
import android.os.Parcel;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.content.pm.ServiceInfo;
import android.content.pm.ResolveInfo;
import java.lang.reflect.Method;
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean callbackActive = false;
    private volatile int callbackEvents = 0;
    private volatile int parsedFrames = 0;
    private volatile int badChecksums = 0;
    private volatile String phase = "БЕЗ ПОЗНАЧКИ";
    private final Map<String,String> previous = new HashMap<String,String>();
    private final Map<String,Map<String,Integer>> phaseCounts = new LinkedHashMap<String,Map<String,Integer>>();
    private volatile long phaseStartedMs = 0;
    private volatile int phaseSequence = 0;
    private final long SETTLE_MS = 3000L;
    private final long SAMPLE_MS = 9000L;
    private final Object statsLock = new Object();
    private final Runnable finishPhase = new Runnable(){public void run(){finishCurrentPhase();}};
    private volatile boolean registerSucceeded = false;
    private IBinder remoteCanbus;
    private Binder callbackBinder;
    private int unregisterCode = -1;
    private final Runnable autoStop = new Runnable(){public void run(){stopCallback();}};


    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(10,7,10,7); root.setBackgroundColor(Color.rgb(14,21,30));
        TextView title = new TextView(this); title.setText("GOLF CAN v1.1  |  SIGNAL ANALYZER"); title.setTextColor(Color.WHITE); title.setTextSize(18); root.addView(title);
        TextView sub = new TextView(this); sub.setText("8227L • SDK " + Build.VERSION.SDK_INT + " • 90 с автостоп • без CAN TX"); sub.setTextColor(Color.LTGRAY); sub.setTextSize(12); root.addView(sub);
        LinearLayout line1 = buttonRow(root);
        addSmallButton(line1,"▶ ПРИЙОМ 90 с",new View.OnClickListener(){public void onClick(View v){confirmCallback();}});
        addSmallButton(line1,"■ СТОП",new View.OnClickListener(){public void onClick(View v){stopCallback();}});
        addSmallButton(line1,"USB ПАПКА",new View.OnClickListener(){public void onClick(View v){chooseUsb();}});
        addSmallButton(line1,"USB ЗВІТ",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){saveToUsb();}}).start();}});
        LinearLayout line2 = buttonRow(root);
        addSmallButton(line2,"ДВЕРІ ВІДКР.",new View.OnClickListener(){public void onClick(View v){mark("ДВЕРІ ВІДКРИТІ");}});
        addSmallButton(line2,"ДВЕРІ ЗАКР.",new View.OnClickListener(){public void onClick(View v){mark("ДВЕРІ ЗАКРИТІ");}});
        addSmallButton(line2,"ЗАПАЛ. ON",new View.OnClickListener(){public void onClick(View v){mark("ЗАПАЛЮВАННЯ ON");}});
        addSmallButton(line2,"ЗАПАЛ. OFF",new View.OnClickListener(){public void onClick(View v){mark("ЗАПАЛЮВАННЯ OFF");}});
        LinearLayout line3 = buttonRow(root);
        addSmallButton(line3,"МАРКЕР: ХОЛОСТІ",new View.OnClickListener(){public void onClick(View v){mark("ДВИГУН ХОЛОСТІ");}});
        addSmallButton(line3,"МАРКЕР: ГАЗ",new View.OnClickListener(){public void onClick(View v){mark("ОБЕРТИ ПІДВИЩЕНО");}});
        addSmallButton(line3,"ДІАГНОСТИКА",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){runAll();}}).start();}});
        addSmallButton(line3,"ПІДСУМОК",new View.OnClickListener(){public void onClick(View v){showSummary();}});
        logView = new TextView(this); logView.setTextColor(Color.rgb(196,236,208)); logView.setTextSize(11); logView.setPadding(8,6,8,6);
        ScrollView sc = new ScrollView(this); sc.setFillViewport(false); sc.addView(logView); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        report = new File(getExternalFilesDir(null),"GolfCanSignalAnalyzer-v1.1-report.txt");
        String saved=getPreferences(0).getString("usb_tree",null); if(saved!=null){usbTree=Uri.parse(saved);}
        log("Старт v1.1. Signal Analyzer. Немає CAN TX; callback тільки за підтвердженням.");
        log("Папка копій: " + getExternalFilesDir(null));
    }
    private LinearLayout buttonRow(LinearLayout root){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);root.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;
    }
    private void addSmallButton(LinearLayout row,String text,View.OnClickListener listener){
        Button b=new Button(this);b.setText(text);b.setTextSize(10);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(2,0,2,0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,44,1);lp.setMargins(2,2,2,2);row.addView(b,lp);b.setOnClickListener(listener);
    }
    private void confirmCallback(){
        new AlertDialog.Builder(this).setTitle("CAN Callback — експеримент")
          .setMessage("CAN callback може вплинути на штатну CAN-програму. Лише на припаркованому авто, 90 секунд, автоматична відписка. Не використовувати під час руху. Продовжити?")
          .setNegativeButton("Скасувати",null)
          .setPositiveButton("Запустити 90 с",(d,w)->new Thread(new Runnable(){public void run(){startCallback();}}).start()).show();
    }
    private int transactionCode(Class<?> stub,String fieldName) throws Exception {
        java.lang.reflect.Field f=stub.getDeclaredField(fieldName);f.setAccessible(true);return f.getInt(null);
    }
    private void startCallback(){
        synchronized(lock){if(callbackActive){log("CALLBACK already running");return;}callbackActive=true;callbackEvents=0;parsedFrames=0;badChecksums=0;registerSucceeded=false;previous.clear(); synchronized(statsLock){phaseCounts.clear();} phase="БЕЗ ПОЗНАЧКИ";phaseStartedMs=0;phaseSequence=0;}
        log("=== CAN LIVE: реєстрація (90 с) ===");
        try{
            Class<?> sm=Class.forName("android.os.ServiceManager");
            Method get=sm.getDeclaredMethod("getService",String.class);
            IBinder remote=(IBinder)get.invoke(null,"canbus_service");
            if(remote==null)throw new IOException("canbus_service відсутній");
            String descriptor=remote.getInterfaceDescriptor();
            if(!"android.os.ICanbusService".equals(descriptor))throw new SecurityException("Неочікуваний дескриптор: "+descriptor);
            Class<?> stub=Class.forName("android.os.ICanbusService$Stub");
            int reg=transactionCode(stub,"TRANSACTION_setCanbusInterface");
            int unreg=transactionCode(stub,"TRANSACTION_removeCanbusInterface");
            log("CAN SERVICE descriptor="+descriptor+" registerCode="+reg+" unregisterCode="+unreg);
            final int callbackCode=transactionCode(Class.forName("android.os.ICanbusInterface$Stub"),"TRANSACTION_onResult");
            log("CALLBACK transactionCode="+callbackCode);
            final Binder local=new Binder(){
                @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws android.os.RemoteException {
                    if(code==IBinder.INTERFACE_TRANSACTION){if(reply!=null)reply.writeString("android.os.ICanbusInterface");return true;}
                    if(code==callbackCode){
                        data.enforceInterface("android.os.ICanbusInterface");
                        byte[] bytes=data.createByteArray();int type=data.readInt();
                        if(callbackActive){
                            int n=++callbackEvents;
                            if(n<=2500){
                                int usable=(bytes==null?0:Math.min(Math.max(type,0),bytes.length));
                                log("CAN_RX #"+n+" PHASE="+phase+" type="+type+" buffer="+(bytes==null?-1:bytes.length)+" payload="+usable+" HEX="+hex(bytes,usable));
                                decodeFrames(bytes,usable,n);
                            } else if(n==2501){log("CAN_RX LIMIT 2500: наступні пакети не записуються");}
                        }
                        if(reply!=null)reply.writeNoException();return true;
                    }
                    return super.onTransact(code,data,reply,flags);
                }
            };
            local.attachInterface(null,"android.os.ICanbusInterface");
            synchronized(lock){remoteCanbus=remote;callbackBinder=local;unregisterCode=unreg;}
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(descriptor);data.writeStrongBinder(local);
                boolean ok=remote.transact(reg,data,reply,0);
                log("REGISTER transact result="+ok);
                if(!ok)throw new IOException("REGISTER transact повернув false");
                reply.readException();
                registerSucceeded=true;log("REGISTER OK — очікуємо onResult(byte[],int) до 90 секунд");
                mainHandler.postDelayed(autoStop,90000);
            }finally{data.recycle();reply.recycle();}
        }catch(Throwable e){log("CALLBACK ERROR "+describe(e));stopCallback();}
    }
    private String hex(byte[] bytes,int limit){if(bytes==null)return "null";StringBuilder sb=new StringBuilder();for(int i=0;i<Math.min(bytes.length,limit);i++){if(i>0)sb.append(' ');sb.append(String.format(Locale.US,"%02X",bytes[i]&255));}if(bytes.length>limit)sb.append(" ...");return sb.toString();}
    private void stopCallback(){
        mainHandler.removeCallbacks(autoStop);mainHandler.removeCallbacks(finishPhase);finishCurrentPhase();
        final IBinder remote;final Binder local;final int unreg;
        synchronized(lock){remote=remoteCanbus;local=callbackBinder;unreg=unregisterCode;remoteCanbus=null;callbackBinder=null;unregisterCode=-1;callbackActive=false;}
        if(local==null){log("CALLBACK не активний. Отримано="+callbackEvents);return;}
        new Thread(new Runnable(){public void run(){
            try{
                if(remote!=null && unreg>=0){Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{
                    data.writeInterfaceToken("android.os.ICanbusService");data.writeStrongBinder(local);
                    boolean ok=remote.transact(unreg,data,reply,0);
                    if(ok)reply.readException();log("UNREGISTER result="+ok);
                }finally{data.recycle();reply.recycle();}}
            }catch(Throwable e){log("UNREGISTER ERROR "+describe(e));}
            showSummary();log("CAN CALLBACK STOP. Подій="+callbackEvents+" frames="+parsedFrames+" badChecksums="+badChecksums);
        }}).start();
    }
    private void mark(String name){
        if(!callbackActive){log("МАРКЕР: спочатку запусти прийом");return;}
        finishCurrentPhase();
        phase=name;
        phaseStartedMs=android.os.SystemClock.elapsedRealtime();
        final int sequence=++phaseSequence;
        log("=== MARK "+name+" #"+sequence+" === events="+callbackEvents+" frames="+parsedFrames);
        log("PHASE #"+sequence+" 0-3 с: перехід, 3-12 с: стабільні дані. Виконай дію одразу після натискання.");
        mainHandler.removeCallbacks(finishPhase);
        mainHandler.postDelayed(finishPhase,SETTLE_MS+SAMPLE_MS);
    }
    private void finishCurrentPhase(){
        String name=phase;
        if("БЕЗ ПОЗНАЧКИ".equals(name))return;
        phase="БЕЗ ПОЗНАЧКИ";
        long elapsed=android.os.SystemClock.elapsedRealtime()-phaseStartedMs;
        log("=== PHASE END "+name+" durationMs="+elapsed+" ===");
        showSummary();
    }
    private void showSummary(){
        log("=== СТАТИСТИКА СТАБІЛЬНИХ ФАЗ (3-12 с після MARK) ===");
        synchronized(statsLock){
            for(Map.Entry<String,Map<String,Integer>> phaseEntry:phaseCounts.entrySet()){
                log("PHASE_STATS "+phaseEntry.getKey());
                for(Map.Entry<String,Integer> value:phaseEntry.getValue().entrySet())
                    log("  CMD_PAYLOAD "+value.getKey()+" count="+value.getValue());
            }
        }
        log("=== КІНЕЦЬ СТАТИСТИКИ ===");
    }
    private void addPhaseSample(String cmd,byte[] bytes,int start,int frameLen){
        String name=phase;
        if("БЕЗ ПОЗНАЧКИ".equals(name))return;
        long age=android.os.SystemClock.elapsedRealtime()-phaseStartedMs;
        if(age<SETTLE_MS || age>SETTLE_MS+SAMPLE_MS)return;
        // Group by command and payload; do not include checksum (derived field).
        String key=cmd+" "+hexRange(bytes,start+3,frameLen-4);
        synchronized(statsLock){
            Map<String,Integer> counts=phaseCounts.get(name);
            if(counts==null){counts=new LinkedHashMap<String,Integer>();phaseCounts.put(name,counts);}
            Integer n=counts.get(key);counts.put(key,n==null?1:n+1);
        }
    }
    private void decodeFrames(byte[] bytes,int len,int event){
        if(bytes==null || len<4)return;
        int offset=0;
        while(offset<len){
            if((bytes[offset]&255)!=0x2E){offset++;continue;}
            if(offset+3>len)break;
            int payload=bytes[offset+2]&255;
            int frameLen=payload+4;
            if(frameLen<4 || frameLen>64 || offset+frameLen>len){
                log("FRAME_PARTIAL event="+event+" offset="+offset+" payload="+payload);offset++;continue;
            }
            int sum=0;for(int i=offset;i<offset+frameLen;i++)sum=(sum+(bytes[i]&255))&255;
            // Raise 2E framing observed: 2E CMD LEN PAYLOAD... CHECKSUM, checksum makes byte sum == 0x2D.
            boolean valid=(sum==0x2D);
            if(!valid)badChecksums++;
            parsedFrames++;
            String key=String.format(Locale.US,"%02X",bytes[offset+1]&255);
            String data=hexRange(bytes,offset,frameLen);
            String prev=previous.put(key,data);
            boolean changed=prev==null||!prev.equals(data);
            if(valid)addPhaseSample(key,bytes,offset,frameLen);
            log("FRAME #"+parsedFrames+" phase="+phase+" cmd="+key+" payload="+payload+" sum="+String.format(Locale.US,"%02X",sum)+" check="+(valid?"OK":"UNKNOWN")+" changed="+changed+" HEX="+data);
            offset+=frameLen;
        }
    }
    private String hexRange(byte[] bytes,int start,int len){
        StringBuilder sb=new StringBuilder();for(int i=start;i<start+len;i++){if(i>start)sb.append(' ');sb.append(String.format(Locale.US,"%02X",bytes[i]&255));}return sb.toString();
    }
    private void runAll(){
        log("========== ПОВНА ДІАГНОСТИКА v0.9 ==========");
        log("Перевірка метаданих без CAN-команд. Callback запускається лише окремо за підтвердженням.");
        log("DEVICE="+Build.MANUFACTURER+" / "+Build.MODEL+" / "+Build.DISPLAY+" SDK="+Build.VERSION.SDK_INT);
        inspectBinder();
        inspectPackages();
        inspectKernel();
        inspectSerialMetadata();
        log("========== КІНЕЦЬ. ВИБЕРИ USB ТА ЗБЕРЕЖИ ЗВІТ ==========");
    }
    private void inspectPackages(){
        log("=== ПАКЕТИ, PERMISSIONS, PROVIDERS ===");
        PackageManager pm=getPackageManager();
        for(String pkg:packages){
            try{
                PackageInfo pi=pm.getPackageInfo(pkg,PackageManager.GET_PERMISSIONS|PackageManager.GET_PROVIDERS|PackageManager.GET_RECEIVERS|PackageManager.GET_SERVICES);
                log("PKG "+pkg+" version="+pi.versionName+" uid="+pi.applicationInfo.uid+" flags="+pi.applicationInfo.flags);
                if(pi.requestedPermissions!=null)for(String perm:pi.requestedPermissions){
                    int grant=pm.checkPermission(perm,getPackageName());
                    if(perm.toLowerCase(Locale.US).contains("can")||perm.toLowerCase(Locale.US).contains("mcu")||perm.toLowerCase(Locale.US).contains("car")||perm.toLowerCase(Locale.US).contains("system"))
                        log("PERM "+perm+" grantedToDiagnostic="+(grant==PackageManager.PERMISSION_GRANTED));
                }
                if(pi.providers!=null)for(android.content.pm.ProviderInfo x:pi.providers)log("PROVIDER "+x.name+" authority="+x.authority+" exported="+x.exported+" readPermission="+x.readPermission);
                if(pi.receivers!=null)for(android.content.pm.ActivityInfo x:pi.receivers)log("RECEIVER "+x.name+" exported="+x.exported+" permission="+x.permission);
            }catch(Throwable e){log("PACKAGE_META "+pkg+" error="+describe(e));}
        }
        log("MY_UID="+android.os.Process.myUid());
        try{Class<?> c=Class.forName("android.os.ICanbusService");log("AIDL_CLASS ICanbusService PRESENT methods="+c.getDeclaredMethods().length);for(Method m:c.getDeclaredMethods())log("AIDL_METHOD "+m.toGenericString());}catch(Throwable e){log("AIDL_CLASS ICanbusService unavailable="+describe(e));}
        try{Class<?> c=Class.forName("android.os.ICanbusInterface");log("AIDL_CLASS ICanbusInterface PRESENT methods="+c.getDeclaredMethods().length);for(Method m:c.getDeclaredMethods())log("AIDL_METHOD "+m.toGenericString());}catch(Throwable e){log("AIDL_CLASS ICanbusInterface unavailable="+describe(e));}
    }
    private void inspectKernel(){
        log("=== SOCKETCAN / PROC / SYS (READ ONLY) ===");
        String[] paths={"/proc/net/dev","/proc/net/can/rcvlist_all","/proc/net/can/stats","/proc/net/can/version","/proc/net/can/rcvlist_fil","/proc/tty/drivers"};
        for(String path:paths)readTextLimited(path,35);
        File net=new File("/sys/class/net");
        File[] items=net.listFiles();
        if(items==null){log("SYS_NET list unavailable");return;}
        for(File f:items){String n=f.getName();if(n.startsWith("can")||n.startsWith("vcan")||n.contains("slcan")){
            log("SOCKETCAN_IF "+n);readTextLimited(f.getAbsolutePath()+"/type",2);readTextLimited(f.getAbsolutePath()+"/operstate",2);
        }}
        log("SOCKETCAN_IF scan complete (absence of interface does not exclude UART CANBOX)");
    }
    private void inspectSerialMetadata(){
        log("=== UART / DEVICE NODES (METADATA ONLY; NO OPEN) ===");
        String[] dirs={"/dev","/dev/serial","/dev/pts"};
        for(String dir:dirs){File d=new File(dir);File[] files=d.listFiles();if(files==null){log("DEV_DIR "+dir+" not readable");continue;}
            int hits=0;for(File f:files){String n=f.getName().toLowerCase(Locale.US);
                if(n.startsWith("tty")||n.contains("uart")||n.contains("can")||n.contains("mcu")||n.contains("serial")){
                    if(++hits<=45)log("DEV_NODE "+f.getAbsolutePath()+" readable="+f.canRead()+" writable="+f.canWrite());
                }
            }log("DEV_DIR "+dir+" matched="+hits+" (display limited to 45)");
        }
    }
    private void readTextLimited(String path,int limit){
        File f=new File(path);log("FILE "+path+" exists="+f.exists()+" readable="+f.canRead());
        if(!f.isFile()||!f.canRead())return;
        try(BufferedReader br=new BufferedReader(new FileReader(f))){String line;int n=0;while((line=br.readLine())!=null&&n++<limit){log("  "+(line.length()>220?line.substring(0,220):line));}}
        catch(Throwable e){log("FILE_ERROR "+path+" "+describe(e));}
    }
    private void inspectBinder(){
        log("=== v0.9: ТІЛЬКИ ЧИТАННЯ МЕТАДАНИХ СИСТЕМНИХ СЕРВІСІВ ===");
        log("SDK="+Build.VERSION.SDK_INT+"; ніяких CAN команд, bindService чи transact");
        try {
            Class<?> sm=Class.forName("android.os.ServiceManager");
            Method list=sm.getDeclaredMethod("listServices");
            String[] names=(String[])list.invoke(null);
            log("ServiceManager.listServices: "+(names==null?"null":names.length));
            if(names!=null){
                Method get=sm.getDeclaredMethod("getService",String.class);
                int candidates=0;
                for(String name:names){
                    String low=name.toLowerCase(Locale.US);
                    if(!(low.contains("can")||low.contains("mcu")||low.contains("uart")||low.contains("vehicle")||low.contains("car")||low.contains("pvc")))continue;
                    candidates++;
                    try{
                        Object o=get.invoke(null,name);
                        if(o instanceof IBinder){
                            IBinder b=(IBinder)o;
                            String descriptor;
                            try{descriptor=b.getInterfaceDescriptor();}catch(Throwable e){descriptor="ERROR:"+e.getClass().getSimpleName()+":"+e.getMessage();}
                            log("BINDER name="+name+" descriptor="+descriptor+" alive="+b.isBinderAlive());
                        }else log("BINDER name="+name+" object="+(o==null?"null":o.getClass().getName()));
                    }catch(Throwable e){log("BINDER "+name+" error="+describe(e));}
                }
                log("Знайдено сервісів із CAN/MCU/CAR у назві: "+candidates);
                if(candidates==0)log("Імена можуть бути іншими. Повний список не читаємо як CAN-повідомлення.");
            }
        }catch(Throwable e){log("ServiceManager недоступний: "+describe(e));}
        PackageManager pm=getPackageManager();
        for(String pkg:packages){
            try{
                PackageInfo info=pm.getPackageInfo(pkg,PackageManager.GET_SERVICES|PackageManager.GET_PERMISSIONS);
                log("PACKAGE "+pkg+" version="+info.versionName);
                if(info.requestedPermissions!=null)for(String perm:info.requestedPermissions){
                    if(perm.toLowerCase(Locale.US).contains("can")||perm.toLowerCase(Locale.US).contains("mcu")||perm.toLowerCase(Locale.US).contains("system")||perm.toLowerCase(Locale.US).contains("signature"))log("REQUESTED_PERMISSION "+perm);
                }
                if(info.services!=null)for(ServiceInfo si:info.services){
                    log("SERVICE "+si.name+" exported="+si.exported+" permission="+si.permission+" process="+si.processName);
                }
            }catch(Throwable e){log("PACKAGE "+pkg+" error="+describe(e));}
        }
        log("=== КІНЕЦЬ BINDER ДІАГНОСТИКИ. НІЧОГО НЕ ВИКЛИКАНО У CAN-СЕРВІСАХ ===");
    }
    private String describe(Throwable e){
        Throwable t=e;
        if(e instanceof java.lang.reflect.InvocationTargetException && ((java.lang.reflect.InvocationTargetException)e).getTargetException()!=null)t=((java.lang.reflect.InvocationTargetException)e).getTargetException();
        return t.getClass().getName()+": "+t.getMessage();
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
    @Override protected void onDestroy(){stopCallback();stopListening();super.onDestroy();}
    private void addButton(LinearLayout root,String text,View.OnClickListener click){Button b=new Button(this);b.setText(text);root.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(click);}
    private void log(String s){final String line="["+new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"] "+s+"\n";synchronized(lock){logs.append(line); if(report!=null){try(FileOutputStream f=new FileOutputStream(report,true)){f.write(line.getBytes("UTF-8"));}catch(Exception ignored){}}}runOnUiThread(new Runnable(){public void run(){if(logView!=null){logView.append(line);}}});}
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
