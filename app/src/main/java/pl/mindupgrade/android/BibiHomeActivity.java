package pl.mindupgrade.android;
import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import androidx.browser.customtabs.CustomTabsIntent;

public final class BibiHomeActivity extends Activity {
    static boolean visible;
    private TextView status,diagnostic;
    private ProgressBar meter;
    private boolean pendingStart, waitingPermission, panelOpen, pendingPanel, dismissRequested;
    private long wakeDeadline;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){ public void run(){render();if(pendingPanel){openIfUnlocked();if(pendingPanel && SystemClock.elapsedRealtime()>wakeDeadline)recoverWake();}if(visible)ui.postDelayed(this,250);} };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(android.view.Gravity.CENTER);root.setBackgroundColor(Color.BLACK);
        int p=dp(20);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(p+i.getSystemWindowInsetLeft(),p+i.getSystemWindowInsetTop(),p+i.getSystemWindowInsetRight(),p+i.getSystemWindowInsetBottom());return i;});
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_logo);logo.setContentDescription("Mind Upgrade");logo.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(logo,new LinearLayout.LayoutParams(-1,dp(170)));
        status=new TextView(this);status.setTextColor(Color.CYAN);status.setTextSize(18);root.addView(status);
        meter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);meter.setMax(100);meter.setContentDescription("Poziom dźwięku mikrofonu");root.addView(meter,new LinearLayout.LayoutParams(-1,dp(16)));
        diagnostic=new TextView(this);diagnostic.setTextColor(Color.WHITE);diagnostic.setTextSize(15);diagnostic.setPadding(0,p,0,p);root.addView(diagnostic);
        button(root,"Włącz Bibi",()->{pendingStart=true;maybeStart();});
        button(root,"Wyłącz Bibi",()->{pendingStart=false;BibiWakeService.stop(this);render();});
        button(root,"Ustaw jako asystenta",this::chooseAssistant);
        button(root,"Ustawienia asystenta Androida",()->startActivity(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)));
        button(root,"Otwórz Mind Upgrade",this::openPanel);
        button(root,"Uprawnienia aplikacji",()->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        TextView help=new TextView(this);help.setTextColor(Color.LTGRAY);help.setTextSize(15);help.setPadding(0,p,0,0);
        help.setText("Po włączeniu sprawdź, czy pasek reaguje na głos. Następnie wyjdź na pulpit, wygasz ekran i powiedz bi-bi, po czym zrób pauzę. Tryb cichy nie jest zmieniany.\n\nPo wybudzeniu zablokowanego telefonu odblokuj go, aby otworzyć panel. Nasłuch używa baterii. Zatrzymasz go także z powiadomienia.");root.addView(help);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
        wake(getIntent());
    }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void button(LinearLayout l,String text,Runnable r){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->r.run());l.addView(b);}
    private void chooseAssistant(){
        if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)){startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),102);return;}}
        startActivity(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS));
    }
    private void maybeStart(){
        if(!pendingStart||!visible||waitingPermission)return;
        // Re-check every time after onResume: a pending permission request is not a grant.
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){waitingPermission=true;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},100);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){waitingPermission=true;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);return;}
        pendingStart=false;
        try{BibiWakeService.status="Uruchamiam nasłuch…";BibiWakeService.start(this);}catch(RuntimeException e){BibiWakeService.status="Nie można uruchomić nasłuchu: "+e.getClass().getSimpleName();}
        render();
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){
        super.onRequestPermissionsResult(code,p,g);waitingPermission=false;
        if(code==100||code==101){
            if(g.length==0||g[0]!=PackageManager.PERMISSION_GRANTED){pendingStart=false;BibiWakeService.status="Brak zgody: "+(code==100?"mikrofon":"powiadomienia")+". Otwórz Uprawnienia aplikacji i udziel zgody.";render();}
            else maybeStart();
        }
    }
    private void render(){
        if(status==null)return;status.setText(BibiWakeService.status);meter.setProgress(BibiWakeService.level);
        boolean selected=BibiAssistantService.selected(this);
        boolean role=false;if(Build.VERSION.SDK_INT>=29){RoleManager r=getSystemService(RoleManager.class);role=r!=null&&r.isRoleHeld(RoleManager.ROLE_ASSISTANT);}
        android.content.SharedPreferences prefs=getSharedPreferences("bibi",0);
        long last=prefs.getLong("last_detected",0);
        diagnostic.setText("Mikrofon: "+(BibiWakeService.listening?"AKTYWNY":"nie nasłuchuje")+" · poziom "+BibiWakeService.level+"%\n"
            +"Usługa asystenta: "+(selected?"wybrana":"niewybrana")+" · połączenie: "+(BibiAssistantService.instance!=null?"gotowe":"brak")
            +"\nRola domyślna: "+(role?"tak":"nie")+"\nWykrycia: "+prefs.getInt("detections",0)+(last==0?"":" · "+android.text.format.DateFormat.format("HH:mm:ss",last))
            +(!selected?"\nNasłuch możesz sprawdzić teraz. Do otwierania z tła wybierz usługę Mind Upgrade w ustawieniach asystenta.":""));
    }
    private void wake(Intent i){
        boolean wake=i.getBooleanExtra("bibi_detected",false);setShowWhenLocked(wake);setTurnScreenOn(wake);
        if(wake){dismissRequested=false;wakeDeadline=SystemClock.elapsedRealtime()+30000;getSharedPreferences("bibi",0).edit().putLong("last_opened",System.currentTimeMillis()).apply();BibiWakeService.status="Usłyszałem Bibi.";pendingPanel=true;}
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);wake(i);if(visible)openIfUnlocked();}
    private void openIfUnlocked(){
        if(!pendingPanel||!visible)return;
        if(!getSystemService(KeyguardManager.class).isKeyguardLocked()){pendingPanel=false;launchPanel();}
        else if(!dismissRequested&&hasWindowFocus())openPanel();
    }
    private void recoverWake(){
        pendingPanel=false;dismissRequested=false;
        if(getSharedPreferences("bibi",0).getBoolean("enabled",false)){pendingStart=true;maybeStart();}
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)openIfUnlocked();}
    private void openPanel(){
        KeyguardManager lock=getSystemService(KeyguardManager.class);
        if(lock.isKeyguardLocked()){
            if(dismissRequested)return;dismissRequested=true;
            lock.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback(){
                @Override public void onDismissSucceeded(){dismissRequested=false;pendingPanel=false;launchPanel();}
                @Override public void onDismissCancelled(){recoverWake();}
                @Override public void onDismissError(){recoverWake();}
            });return;
        }
        pendingPanel=false;launchPanel();
    }
    private void launchPanel(){
        if(panelOpen)return;
        BibiWakeService.pause();panelOpen=true;
        try{new CustomTabsIntent.Builder().setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK).setShowTitle(true).build()
            .launchUrl(this,Uri.parse("https://kupiec-techniczny-piotr.bad83teddy666.chatgpt.site/"));}
        catch(ActivityNotFoundException e){panelOpen=false;recoverWake();BibiWakeService.status="Brak przeglądarki. Włącz przeglądarkę w telefonie.";}
    }
    @Override protected void onResume(){super.onResume();visible=true;ui.removeCallbacks(poll);ui.post(poll);
        if(panelOpen){panelOpen=false;if(getSharedPreferences("bibi",0).getBoolean("enabled",false))pendingStart=true;}
        maybeStart();openIfUnlocked();}
    @Override protected void onPause(){visible=false;ui.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
