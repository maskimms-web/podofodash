package com.golf.candiagnostic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
    private DashboardView dashboard;
    private LinearLayout diagnosticPanel;
    private ScrollView diagnosticScroll;
    private boolean diagnosticsVisible=false;
    private TextView doorView, ignitionView, evidenceView;
    private volatile int door41=-1, door24=-1, ignition41=-1, ignition24=-1;
    private volatile long door41At=0, door24At=0, ignition41At=0, ignition24At=0;
    private volatile String shownDoor="", shownIgnition="";
    // Raise-PQ 0x41/0x02: candidate telemetry mapping, never presented as verified.
    private volatile int rpmCandidate=-1, speedCandidate=-1, voltageCentivolts=-1;
    private volatile int outsideTempTenths=Integer.MIN_VALUE, fuelRaw=-1, mileageCandidate=-1;
    private volatile long telemetryAt=0;
    private volatile String telemetryHex="";
    private volatile int telemetryFrames=0;
    private static final long TELEMETRY_TTL_MS=12000L;
    private TextView telemetryView;
    private final Map<String,Integer> commandCounts=new TreeMap<String,Integer>();
    private final Map<String,String> lastCommandHex=new TreeMap<String,String>();
    private final Map<String,Integer> changedCounts=new TreeMap<String,Integer>();
    private static final long SIGNAL_TTL_MS=12000L;
    private final Object signalLock=new Object();
    private final java.io.ByteArrayOutputStream rxStream=new java.io.ByteArrayOutputStream();
    private final Runnable refreshSignals=new Runnable(){public void run(){updateSignalDisplay();mainHandler.postDelayed(this,1000);}};
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
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(Color.rgb(7,12,22));
        LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);toolbar.setPadding(8,2,8,2);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,43));
        addSmallButton(toolbar,"▶ CAN 90 с",new View.OnClickListener(){public void onClick(View v){confirmCallback();}});
        addSmallButton(toolbar,"■ СТОП",new View.OnClickListener(){public void onClick(View v){stopCallback();}});
        addSmallButton(toolbar,"ДІАГНОСТИКА",new View.OnClickListener(){public void onClick(View v){diagnosticsVisible=!diagnosticsVisible;diagnosticScroll.setVisibility(diagnosticsVisible?View.VISIBLE:View.GONE);dashboard.setVisibility(diagnosticsVisible?View.GONE:View.VISIBLE);}});
        dashboard=new DashboardView(this);root.addView(dashboard,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView diagScroll=new ScrollView(this);diagnosticScroll=diagScroll;diagScroll.setVisibility(View.GONE);diagnosticPanel=new LinearLayout(this);diagnosticPanel.setOrientation(1);diagnosticPanel.setPadding(10,7,10,7);diagnosticPanel.setBackgroundColor(Color.rgb(14,21,30));diagScroll.addView(diagnosticPanel);
        root.addView(diagScroll,new LinearLayout.LayoutParams(-1,0,1));
        // Diagnostic view is accessible with the toolbar button; the dashboard is the default.
        final LinearLayout oldRoot=diagnosticPanel;

        TextView title = new TextView(this); title.setText("GOLF R CAN v1.5  |  RAISE-PQ ANALYZER"); title.setTextColor(Color.WHITE); title.setTextSize(18); oldRoot.addView(title);
        TextView sub = new TextView(this); sub.setText("8227L • SDK " + Build.VERSION.SDK_INT + " • 90 с автостоп • без CAN TX"); sub.setTextColor(Color.LTGRAY); sub.setTextSize(12); oldRoot.addView(sub);
        LinearLayout indicators=new LinearLayout(this);indicators.setOrientation(LinearLayout.HORIZONTAL);
        oldRoot.addView(indicators,new LinearLayout.LayoutParams(-1,104));
        doorView=indicator(indicators,"ДВЕРІ\nНЕВІДОМО",Color.rgb(65,75,88));
        ignitionView=indicator(indicators,"ЗАПАЛЮВАННЯ\nНЕВІДОМО",Color.rgb(65,75,88));
        evidenceView=new TextView(this);evidenceView.setText("Двері: основний сигнал 0x24. 0x41 лише діагностика. Без CAN TX.");
        evidenceView.setTextColor(Color.LTGRAY);evidenceView.setTextSize(11);oldRoot.addView(evidenceView);
        telemetryView=new TextView(this);telemetryView.setTextColor(Color.rgb(146,207,255));telemetryView.setTextSize(15);telemetryView.setPadding(10,10,10,10);oldRoot.addView(telemetryView);
        LinearLayout telemetryButtons=buttonRow(oldRoot);
        addSmallButton(telemetryButtons,"КАТАЛОГ CMD",new View.OnClickListener(){public void onClick(View v){showCommandCatalog();}});
        addSmallButton(telemetryButtons,"0x41 HEX",new View.OnClickListener(){public void onClick(View v){showTelemetryHex();}});
        addSmallButton(telemetryButtons,"ПОЗНАЧКА: 1500 RPM",new View.OnClickListener(){public void onClick(View v){mark("RPM 1500 (зі штатного тахометра)");}});
        addSmallButton(telemetryButtons,"ПОЗНАЧКА: 2000 RPM",new View.OnClickListener(){public void onClick(View v){mark("RPM 2000 (зі штатного тахометра)");}});
        LinearLayout line1 = buttonRow(oldRoot);
        addSmallButton(line1,"▶ ПРИЙОМ 90 с",new View.OnClickListener(){public void onClick(View v){confirmCallback();}});
        addSmallButton(line1,"■ СТОП",new View.OnClickListener(){public void onClick(View v){stopCallback();}});
        addSmallButton(line1,"USB ПАПКА",new View.OnClickListener(){public void onClick(View v){chooseUsb();}});
        addSmallButton(line1,"USB ЗВІТ",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){saveToUsb();}}).start();}});
        LinearLayout line2 = buttonRow(oldRoot);
        addSmallButton(line2,"ДВЕРІ ВІДКР.",new View.OnClickListener(){public void onClick(View v){mark("ДВЕРІ ВІДКРИТІ");}});
        addSmallButton(line2,"ДВЕРІ ЗАКР.",new View.OnClickListener(){public void onClick(View v){mark("ДВЕРІ ЗАКРИТІ");}});
        addSmallButton(line2,"ЗАПАЛ. ON",new View.OnClickListener(){public void onClick(View v){mark("ЗАПАЛЮВАННЯ ON");}});
        addSmallButton(line2,"ЗАПАЛ. OFF",new View.OnClickListener(){public void onClick(View v){mark("ЗАПАЛЮВАННЯ OFF");}});
        LinearLayout line3 = buttonRow(oldRoot);
        addSmallButton(line3,"МАРКЕР: ХОЛОСТІ",new View.OnClickListener(){public void onClick(View v){mark("ДВИГУН ХОЛОСТІ");}});
        addSmallButton(line3,"МАРКЕР: ГАЗ",new View.OnClickListener(){public void onClick(View v){mark("ОБЕРТИ ПІДВИЩЕНО");}});
        addSmallButton(line3,"ДІАГНОСТИКА",new View.OnClickListener(){public void onClick(View v){new Thread(new Runnable(){public void run(){runAll();}}).start();}});
        addSmallButton(line3,"ПІДСУМОК",new View.OnClickListener(){public void onClick(View v){showSummary();}});
        logView = new TextView(this); logView.setTextColor(Color.rgb(196,236,208)); logView.setTextSize(11); logView.setPadding(8,6,8,6);
        ScrollView sc = new ScrollView(this); sc.setFillViewport(false); sc.addView(logView); oldRoot.addView(sc,new LinearLayout.LayoutParams(-1,320)); setContentView(root);
        report = new File(getExternalFilesDir(null),"GolfR-CAN-v1.5-report.txt");
        String saved=getPreferences(0).getString("usb_tree",null); if(saved!=null){usbTree=Uri.parse(saved);}
        mainHandler.post(refreshSignals);
        log("Старт v1.5. Raise-PQ decoder candidates. Немає CAN TX; callback тільки за підтвердженням.");
        log("Папка копій: " + getExternalFilesDir(null));
    }

    // v1.5 UI: custom Canvas, scaled to 1024x510 below the toolbar.
    // All telemetry values originate from live 0x41/02 candidate decoding; no sample values.
    private final class DashboardView extends View {
        private final Paint p=new Paint(3);
        private final int bg=Color.rgb(3,9,19),blue=Color.rgb(20,118,249),bright=Color.rgb(46,164,255);
        private final int white=Color.rgb(238,246,255),muted=Color.rgb(136,167,200),red=Color.rgb(251,61,79),green=Color.rgb(36,211,129);
        DashboardView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);p.setShader(null);}
        private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(android.graphics.Typeface.create("sans-serif",bold?1:0));p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);}
        private void left(Canvas c,String s,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(android.graphics.Typeface.create("sans-serif",bold?1:0));p.setTextSize(size);p.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,p);}
        private void rect(Canvas c,float x,float y,float w,float h,int color,float r){fill(color);c.drawRoundRect(new RectF(x,y,x+w,y+h),r,r,p);}
        private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);p.setStyle(Paint.Style.FILL);}
        private void ring(Canvas c,float x,float y,float radius,float start,float sweep,int color,float width){fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(width);c.drawArc(new RectF(x-radius,y-radius,x+radius,y+radius),start,sweep,false,p);p.setStyle(Paint.Style.FILL);}
        private void dial(Canvas c,float x,float y,boolean rpm,boolean fresh){
            int max=rpm?8:260;float value=fresh?(rpm?rpmCandidate/1000f:speedCandidate):0f;
            ring(c,x,y,137,133,274,Color.rgb(15,40,75),12);ring(c,x,y,137,133,274,blue,5);
            if(fresh)ring(c,x,y,137,133,Math.max(0,Math.min(274,value/max*274)),bright,9);
            for(int i=0;i<=12;i++){double angle=Math.toRadians(133+i*274.0/12);float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);line(c,x+cs*112,y+sn*112,x+cs*(i%3==0?127:121),y+sn*(i%3==0?127:121),i%3==0?white:muted,i%3==0?3:1);}
            for(int i=0;i<=4;i++){double angle=Math.toRadians(133+i*274.0/4);float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);text(c,String.valueOf(rpm?i*2:i*65),x+cs*91,y+sn*91+6,19,white,true);}
            text(c,rpm?"ОБЕРТИ":"ШВИДКІСТЬ",x,y-32,14,muted,false);
            text(c,fresh?String.valueOf(rpm?rpmCandidate:speedCandidate):"—",x,y+19,61,white,true);
            text(c,rpm?"об/хв":"км/год",x,y+48,15,muted,false);
            text(c,"0x41 / 0x02  •  КАНДИДАТ",x,y+100,10,muted,false);
        }
        private void tile(Canvas c,float x,float y,float w,String label,String value,String unit,int accent){
            rect(c,x,y,w,68,Color.rgb(10,23,43),10);fill(Color.rgb(24,68,113));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);c.drawRoundRect(new RectF(x,y,x+w,y+68),10,10,p);p.setStyle(Paint.Style.FILL);
            rect(c,x+10,y+13,3,40,accent,2);left(c,label,x+22,y+23,13,muted,true);left(c,value+(unit.length()>0?" "+unit:""),x+22,y+52,23,white,true);
        }
        @Override protected void onDraw(Canvas real){super.onDraw(real);real.save();real.scale(getWidth()/1024f,getHeight()/510f);Canvas c=real;c.drawColor(bg);
            rect(c,0,0,1024,46,Color.rgb(6,18,36),0);line(c,0,46,1024,46,blue,2);
            text(c,"GOLF",466,34,31,white,true);text(c,"R",553,35,39,bright,true);
            left(c,"CAN  •  RAISE-PQ",20,30,16,muted,true);
            text(c,callbackActive&&registerSucceeded?"● CAN ОНЛАЙН":"○ CAN ВИМКНЕНО",867,30,15,callbackActive&&registerSucceeded?green:muted,true);
            boolean fresh=telemetryFresh();dial(c,172,197,true,fresh);dial(c,853,197,false,fresh);
            rect(c,337,63,349,272,Color.rgb(8,23,44),18);
            // Vector top-down Golf silhouette; driver-side accent represents observed 0x24 state only.
            rect(c,456,91,110,221,Color.rgb(28,49,73),30);
            rect(c,465,100,92,203,Color.rgb(93,123,153),26);
            rect(c,472,112,78,43,Color.rgb(16,37,63),15);
            rect(c,472,164,78,85,Color.rgb(10,27,47),15);
            rect(c,472,259,78,34,Color.rgb(16,37,63),12);
            rect(c,448,143,13,47,Color.rgb(4,9,17),4);rect(c,561,143,13,47,Color.rgb(4,9,17),4);
            rect(c,448,231,13,45,Color.rgb(4,9,17),4);rect(c,561,231,13,45,Color.rgb(4,9,17),4);
            long now=android.os.SystemClock.elapsedRealtime();boolean doorFresh=callbackActive&&registerSucceeded&&door24At>0&&now-door24At<SIGNAL_TTL_MS;
            int state=doorFresh?door24:-1;int doorColor=state==1?red:state==0?green:muted;
            if(state==1){line(c,463,165,427,193,red,9);line(c,427,193,423,246,red,8);}else{line(c,464,165,464,236,doorColor,7);}
            text(c,state==1?"ДВЕРІ ВІДКРИТО":state==0?"ДВЕРІ ЗАКРИТО":"ДВЕРІ: НЕВІДОМО",512,329,17,doorColor,true);
            text(c,"Перевірено лише водійські двері",512,351,12,muted,false);
            String fuel=fresh?fuelRaw+" raw":"—";
            String volts=fresh?String.format(Locale.US,"%.2f",voltageCentivolts/100.0):"—";
            String odo=fresh?String.valueOf(mileageCandidate):"—";
            String temp=fresh?String.format(Locale.US,"%.1f",outsideTempTenths/10.0):"—";
            tile(c,18,368,239,"ПАЛИВО  •  RAW",fuel,"",bright);
            tile(c,268,368,239,"ЗОВНІШНЯ ТЕМП.  •  ?",temp,fresh?"°C":"",bright);
            tile(c,518,368,239,"НАПРУГА  •  ?",volts,fresh?"В":"",bright);
            tile(c,768,368,238,"ПРОБІГ  •  ?",odo,fresh?"км":"",bright);
            rect(c,18,447,988,47,Color.rgb(9,28,53),10);
            text(c,"ДІАГНОСТИКА CAN",150,476,17,white,true);
            text(c,"СИРИЙ ПОТІК / КАТАЛОГ",404,476,16,white,true);
            text(c,"0x41 / 0x02 HEX",680,476,16,white,true);
            text(c,"НЕПІДТВЕРДЖЕНІ ДАНІ",892,476,12,muted,true);
            real.restore();
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=android.view.MotionEvent.ACTION_UP)return true;
            float x=e.getX()*1024f/getWidth(),y=e.getY()*510f/getHeight();
            if(y>440){if(x<270){diagnosticsVisible=true;diagnosticScroll.setVisibility(View.VISIBLE);dashboard.setVisibility(View.GONE);}
                else if(x<560){showCommandCatalog();}else if(x<780){showTelemetryHex();}
                else{new AlertDialog.Builder(MainActivity.this).setTitle("Статус даних").setMessage("0x41/02: RPM, швидкість, напруга, зовнішня температура, пробіг і паливо — кандидати. Паливо у RAW, не %. Температура ОЖ невідома. 0x24 перевірено лише при відкриванні водійських дверей. Значення не використовувати для керування автомобілем.").setPositiveButton("OK",null).show();}}
            return true;
        }
    }
    private TextView indicator(LinearLayout row,String initial,int background){
        TextView t=new TextView(this);t.setGravity(android.view.Gravity.CENTER);t.setText(initial);
        t.setTextColor(Color.WHITE);t.setTextSize(20);t.setTypeface(null,1);
        t.setBackgroundColor(background);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(3,2,3,2);row.addView(t,lp);return t;
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
        synchronized(lock){if(callbackActive){log("CALLBACK already running");return;}callbackActive=true;resetSignals();callbackEvents=0;parsedFrames=0;badChecksums=0;registerSucceeded=false;previous.clear(); synchronized(statsLock){phaseCounts.clear();} phase="БЕЗ ПОЗНАЧКИ";phaseStartedMs=0;phaseSequence=0;}
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
        updateSignalDisplay();
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
        if(!"БЕЗ ПОЗНАЧКИ".equals(phase)){
            long remain=SETTLE_MS+SAMPLE_MS-(android.os.SystemClock.elapsedRealtime()-phaseStartedMs);
            if(remain>0){log("MARK BLOCKED: дочекайся завершення фази ще "+((remain+999)/1000)+" с");
                Toast.makeText(this,"Зачекай ще "+((remain+999)/1000)+" с",Toast.LENGTH_SHORT).show();return;}
        }
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
    // Callback chunks can split frames. Keep a bounded stream and resynchronize on 0x2E.
    private void decodeFrames(byte[] bytes,int len,int event){
        if(bytes==null || len<=0)return;
        synchronized(signalLock){
            rxStream.write(bytes,0,Math.min(len,bytes.length));
            byte[] all=rxStream.toByteArray();int offset=0;
            while(offset<all.length){
                if((all[offset]&255)!=0x2E){offset++;continue;}
                if(all.length-offset<3)break;
                int payload=all[offset+2]&255;int frameLen=payload+4;
                if(frameLen<4||frameLen>64){offset++;continue;}
                if(all.length-offset<frameLen){
                    // Avoid an invalid header stalling a valid complete frame later in the buffer.
                    int next=-1;
                    for(int j=offset+1;j+5<all.length;j++){
                        if((all[j]&255)!=0x2E)continue;
                        int n=(all[j+2]&255)+4;
                        if(n<4||n>64||j+n>all.length)continue;
                        int sm=0;for(int k=j;k<j+n;k++)sm=(sm+(all[k]&255))&255;
                        if(sm==0x2D){next=j;break;}
                    }
                    if(next>=0){offset=next;continue;}
                    break;
                }
                int sum=0;for(int i=offset;i<offset+frameLen;i++)sum=(sum+(all[i]&255))&255;
                if(sum!=0x2D){badChecksums++;offset++;continue;}
                parsedFrames++;
                String key=String.format(Locale.US,"%02X",all[offset+1]&255);
                String data=hexRange(all,offset,frameLen);
                String prev=previous.put(key,data);
                boolean changed=prev==null||!prev.equals(data);
                synchronized(statsLock){commandCounts.put(key,commandCounts.containsKey(key)?commandCounts.get(key)+1:1);lastCommandHex.put(key,data);if(changed)changedCounts.put(key,changedCounts.containsKey(key)?changedCounts.get(key)+1:1);}
                addPhaseSample(key,all,offset,frameLen);
                analyzeSignals(all,offset,frameLen);
                log("FRAME #"+parsedFrames+" phase="+phase+" cmd="+key+" check=OK changed="+changed+" HEX="+data);
                offset+=frameLen;
            }
            rxStream.reset();
            if(offset<all.length){int keep=Math.min(all.length-offset,64);rxStream.write(all,all.length-keep,keep);}
        }
    }
    private void analyzeSignals(byte[] b,int pos,int frameLen){
        int cmd=b[pos+1]&255, len=b[pos+2]&255;
        long now=android.os.SystemClock.elapsedRealtime();
        if(cmd==0x41 && len==13 && (b[pos+3]&255)==0x02){
            // Payload: 02 RPMhi RPMlo SPEEDhi SPEEDlo Vhi Vlo Thi Tlo ODOhi ODOmid ODOlo FUEL.
            // This is a hypothesis based on a Raise VW(PQ) community decoder, not validated for this CANBOX.
            rpmCandidate=((b[pos+4]&255)<<8)|(b[pos+5]&255);
            speedCandidate=((b[pos+6]&255)<<8)|(b[pos+7]&255);
            voltageCentivolts=((b[pos+8]&255)<<8)|(b[pos+9]&255);
            int tempRaw=((b[pos+10]&255)<<8)|(b[pos+11]&255);
            outsideTempTenths=(short)tempRaw;
            mileageCandidate=((b[pos+12]&255)<<16)|((b[pos+13]&255)<<8)|(b[pos+14]&255);
            fuelRaw=b[pos+15]&255;
            telemetryAt=now;telemetryFrames++;telemetryHex=hexRange(b,pos,frameLen);
        }
        if(cmd==0x41 && len==2){
            int a=b[pos+3]&255, v=b[pos+4]&255;
            if(a==1){
                if(v==0x80||v==0x81||v==0xA0||v==0xA1){door41=(v&1);door41At=now;}
                else {door41=-1;door41At=0;}
                if(v==0x80||v==0x81) {ignition41=0;ignition41At=now;}
                else if(v==0xA0||v==0xA1) {ignition41=1;ignition41At=now;}
            }
        }
        if(cmd==0x24 && len==2){
            int a=b[pos+3]&255,v=b[pos+4]&255;
            if((a==0||a==2||a==4||a==6) && (v==0||v==1)){
                door24=v;door24At=now;
                if(a==0||a==2){ignition24=(a==2?1:0);ignition24At=now;}
            }
        }
        mainHandler.post(new Runnable(){public void run(){updateSignalDisplay();}});
    }
    private void resetSignals(){
        synchronized(signalLock){door41=door24=ignition41=ignition24=-1;
            door41At=door24At=ignition41At=ignition24At=0;rxStream.reset();
            rpmCandidate=speedCandidate=voltageCentivolts=fuelRaw=mileageCandidate=-1;
            outsideTempTenths=Integer.MIN_VALUE;telemetryAt=0;telemetryFrames=0;telemetryHex="";}
        synchronized(statsLock){commandCounts.clear();lastCommandHex.clear();changedCounts.clear();}
        shownDoor="";shownIgnition="";
    }
    private void updateSignalDisplay(){
        if(doorView==null)return;
        long now=android.os.SystemClock.elapsedRealtime();
        boolean live=callbackActive && registerSucceeded;
        boolean a=live && now-door41At<SIGNAL_TTL_MS && door41At>0;
        boolean b=live && now-door24At<SIGNAL_TTL_MS && door24At>0;
        String door="НЕВІДОМО";
        if(b)door=(door24==1?"ВІДКРИТІ":"ЗАКРИТІ");
        boolean c=live&&now-ignition41At<SIGNAL_TTL_MS&&ignition41At>0;
        boolean d=live&&now-ignition24At<SIGNAL_TTL_MS&&ignition24At>0;
        String ign=(c&&d&&ignition41==ignition24)?(ignition41==1?"ON":"OFF"):"НЕВІДОМО";
        doorView.setText("ДВЕРІ (тест водійських)\n"+door);
        doorView.setBackgroundColor("ВІДКРИТІ".equals(door)?Color.rgb(155,45,45):"ЗАКРИТІ".equals(door)?Color.rgb(28,115,68):Color.rgb(65,75,88));
        ignitionView.setText("ЗАПАЛЮВАННЯ\n"+ign);
        ignitionView.setBackgroundColor("ON".equals(ign)?Color.rgb(27,100,140):Color.rgb(65,75,88));
        evidenceView.setText("41: "+(a?(door41==1?"OPEN":"CLOSED"):"—")+"   24: "+(b?(door24==1?"OPEN":"CLOSED"):"—")+"   |  результат: "+(door.equals("ВІДКРИТІ")?"OPEN":door.equals("ЗАКРИТІ")?"CLOSED":"UNKNOWN")+" (основний: 24)");
        if(!door.equals(shownDoor)){log("DOOR_INDICATOR "+shownDoor+" -> "+door+" (41="+(a?door41:-1)+",24="+(b?door24:-1)+", source=24, 41confirm="+(a&&door41==door24)+")");shownDoor=door;}
        if(!ign.equals(shownIgnition)){log("IGNITION_INDICATOR "+shownIgnition+" -> "+ign);shownIgnition=ign;}
        if(telemetryView!=null){boolean fresh=telemetryFresh();telemetryView.setText(
            "0x41/02 • КАНДИДАТИ, НЕ ПІДТВЕРДЖЕНО • кадрів: "+telemetryFrames+"\n"+
            "RPM: "+(fresh?rpmCandidate:"—")+"   Швидкість: "+(fresh?speedCandidate:"—")+" км/год\n"+
            "Напруга: "+(fresh?String.format(Locale.US,"%.2f",voltageCentivolts/100.0):"—")+" V   Зовн. t°: "+(fresh?String.format(Locale.US,"%.1f",outsideTempTenths/10.0):"—")+" °C\n"+
            "Пробіг: "+(fresh?mileageCandidate:"—")+" (кандидат)   Паливо: "+(fresh?fuelRaw:"—")+" raw (НЕ %)\n"+
            "Немає свіжого пакета = —. Температура ОЖ поки не визначена.");}
        if(dashboard!=null)dashboard.invalidate();
    }
    private boolean telemetryFresh(){return callbackActive&&registerSucceeded&&telemetryAt>0&&android.os.SystemClock.elapsedRealtime()-telemetryAt<TELEMETRY_TTL_MS;}
    private void showTelemetryHex(){new AlertDialog.Builder(this).setTitle("0x41/02: останній кадр").setMessage("Пакетів: "+telemetryFrames+"\n"+(telemetryHex.length()==0?"Ще немає пакетів":telemetryHex)+"\n\nПоля кандидати, потрібна перевірка зі штатною панеллю. Паливо відображається RAW, не відсотки.").setPositiveButton("OK",null).show();}
    private void showCommandCatalog(){StringBuilder sb=new StringBuilder();synchronized(statsLock){for(String cmd:commandCounts.keySet()){sb.append("CMD ").append(cmd).append("  кадрів=").append(commandCounts.get(cmd)).append("  змін=").append(changedCounts.get(cmd)).append("\n").append(lastCommandHex.get(cmd)).append("\n\n");}}if(sb.length()==0)sb.append("Поки немає прийнятих кадрів.");new AlertDialog.Builder(this).setTitle("Каталог команд CANBOX").setMessage(sb.toString()).setPositiveButton("OK",null).show();}
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
    @Override protected void onDestroy(){mainHandler.removeCallbacks(refreshSignals);stopCallback();stopListening();super.onDestroy();}
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
