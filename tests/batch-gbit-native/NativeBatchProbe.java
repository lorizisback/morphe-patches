package app.local.curve.screen.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Signed-out emulator only. Synthetic purchases never reach a financial service. */
public final class NativeBatchProbe extends Instrumentation {
    private Bundle args;
    private Activity activity;
    private final CountDownLatch ready=new CountDownLatch(1);
    @Override public void callApplicationOnCreate(Application app) { super.callApplicationOnCreate(app); ready.countDown(); }
    @Override public void onCreate(Bundle value) { super.onCreate(value); args=value; start(); }
    private interface Check { void run() throws Exception; }
    private void onMain(Check check) throws Exception {
        AtomicReference<Exception> failure=new AtomicReference<>();
        runOnMainSync(()-> { try { check.run(); } catch(Exception e) { failure.set(e); } });
        if(failure.get()!=null) throw failure.get();
    }
    private static void require(boolean condition,String message) throws Exception { if(!condition) throw new Exception(message); }
    private static Object get(Object owner,String name) throws Exception {
        Field f=(owner instanceof Class?(Class<?>)owner:owner.getClass()).getDeclaredField(name); f.setAccessible(true);
        return f.get(owner instanceof Class?null:owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner,value);
    }
    private static Object call(Object owner,String name,Object... values) throws Exception {
        Class<?> type=owner instanceof Class?(Class<?>)owner:owner.getClass();
        for(Method method:type.getDeclaredMethods()) if(method.getName().equals(name) && method.getParameterCount()==values.length) {
            method.setAccessible(true);
            try { return method.invoke(owner instanceof Class?null:owner,values); }
            catch(InvocationTargetException e) { if(e.getCause() instanceof Exception) throw (Exception)e.getCause(); throw e; }
        }
        throw new NoSuchMethodException(name);
    }
    private static <T extends View> T find(View view,Class<T> type) {
        if(type.isInstance(view)) return type.cast(view);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            T found=find(((ViewGroup)view).getChildAt(i),type); if(found!=null) return found;
        }
        return null;
    }
    private void settle() throws Exception { waitForIdleSync(); Thread.sleep(250); }
    private void screenshot(String name) throws Exception {
        settle(); android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        } finally { bitmap.recycle(); }
    }
    private void checkDates(ClassLoader loader,Class<?> bridge) throws Exception {
        Class<?> model=loader.loadClass("com.curve.transactions.api.service.model.TransactionNetworkModel");
        Constructor<?> constructor=Arrays.stream(model.getConstructors()).filter(c->c.getParameterCount()==31).findFirst().orElseThrow();
        Instant expected=Instant.now().minusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Object[] values=new Object[31];
        values[0]="fixture-one"; values[2]="card-a"; values[3]=1250L; values[4]=2; values[5]="GBP";
        values[9]="Test shop"; values[10]=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(expected);
        values[17]="debit"; values[20]=false;
        Object purchase=constructor.newInstance(values);
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            JSONArray rows=(JSONArray)call(bridge,"purchases",Collections.singletonList(purchase));
            require(rows.length()==1,"Native REST purchase must load");
            require(expected.toString().equals(rows.getJSONObject(0).getString("date")),"Native REST timestamp must preserve UTC");
            require("12.50 GBP".equals(rows.getJSONObject(0).getString("display")),"Native REST amount must survive mapping");
        } finally { TimeZone.setDefault(previous); }
    }
    public static final class SuspendedFixture {
        final Object marker; final CountDownLatch started=new CountDownLatch(1); final AtomicInteger requests=new AtomicInteger();
        SuspendedFixture(Object marker) { this.marker=marker; }
        public Object await(Object continuation) { requests.incrementAndGet(); started.countDown(); return marker; }
    }
    private void checkCancellation(ClassLoader loader,Class<?> bridge) throws Exception {
        Object marker=Enum.valueOf((Class)loader.loadClass("zo2"),"COROUTINE_SUSPENDED");
        SuspendedFixture fixture=new SuspendedFixture(marker); AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread waiter=new Thread(()-> {
            try {
                try { call(bridge,"suspend",fixture,"await",new Object[0]); throw new Exception("Wait was not cancelled"); }
                catch(InterruptedException expected) { require(Thread.currentThread().isInterrupted(),"Cancellation flag must remain set"); }
                try { call(bridge,"suspend",fixture,"await",new Object[0]); throw new Exception("Cancelled worker started another request"); }
                catch(InterruptedException expected) { }
            } catch(Throwable e) { failure.set(e); }
        },"batch-cancellation-test");
        waiter.start();
        try {
            require(fixture.started.await(3,TimeUnit.SECONDS),"Fake request did not start"); waiter.interrupt(); waiter.join(3000);
            require(!waiter.isAlive(),"Cancelled wait must finish promptly");
            if(failure.get()!=null) throw new Exception("Cancellation regression",failure.get());
            require(fixture.requests.get()==1,"No subsequent native request after cancellation");
        } finally { waiter.interrupt(); }
    }
    private void checkSelection() throws Exception {
        onMain(()-> {
            ((Handler)get(activity,"main")).removeCallbacksAndMessages(null);
            set(activity,"busy",false); ((TextView)get(activity,"error")).setText("");
            Map<String,String> cards=new LinkedHashMap<>(); cards.put("card-a","Test Mastercard · 1234"); cards.put("card-b","Test Visa · 5678"); set(activity,"cards",cards);
            JSONArray rows=new JSONArray().put(new JSONObject().put("id","one").put("title","Test shop").put("display","12.50 GBP")
                .put("source","card-a").put("date",Instant.now().minusSeconds(86400).toString()).put("state","pending"));
            set(activity,"rows",rows); set(activity,"ageDays",90); call(activity,"renderSelection");
            LinearLayout content=(LinearLayout)get(activity,"content"); CompoundButton checkbox=find(content,CompoundButton.class);
            require(checkbox!=null,"Native purchase checkbox missing");
            checkbox.setFocusableInTouchMode(true); require(checkbox.requestFocus(),"Checkbox must accept keyboard focus");
            checkbox.performClick();
            require(find(content,CompoundButton.class)==checkbox,"Selection must retain checkbox identity");
            require(checkbox.hasFocus() && checkbox.isChecked(),"Selection must preserve focus and checked state");
            ((Button)get(activity,"secondary")).performClick();
            require(!checkbox.isChecked() && find(content,CompoundButton.class)==checkbox,"Clear selection must retain row");
            ((Button)get(activity,"secondary")).performClick();
            require(checkbox.isChecked(),"Select all must update existing checkbox");
        });
        screenshot("batch-selection.png");
    }
    private void checkCards(Class<?> bridge) throws Exception {
        onMain(()-> {
            Map<String,Integer> art=(Map)get(bridge,"artworks");
            art.put("card-a",activity.getResources().getIdentifier("card_default_mastercard","drawable",activity.getPackageName()));
            art.put("card-b",activity.getResources().getIdentifier("card_default_visa","drawable",activity.getPackageName()));
            JSONObject quote=new JSONObject().put("charge",new JSONObject().put("minor",1250).put("exponent",2).put("currency","GBP")).put("fees",new JSONArray());
            Map<String,List<JSONObject>> quotes=(Map)get(activity,"quotes"); quotes.put("card-a",Collections.singletonList(quote)); quotes.put("card-b",Collections.singletonList(quote));
            Map<String,Integer> supported=(Map)get(activity,"supported"); supported.put("card-a",1); supported.put("card-b",1);
            call(activity,"renderCards");
        });
        settle();
        onMain(()-> {
            LinearLayout content=(LinearLayout)get(activity,"content"); HorizontalScrollView carousel=find(content,HorizontalScrollView.class);
            require(carousel!=null,"Native card carousel missing");
            carousel.scrollTo(10000,0); int position=carousel.getScrollX(); require(position>0,"Fixture must overflow horizontally");
            ViewGroup items=(ViewGroup)carousel.getChildAt(0); Button target=find(items.getChildAt(1),Button.class);
            target.setFocusableInTouchMode(true); require(target.requestFocus(),"Card button must accept focus");
            target.performClick();
            require(find(content,HorizontalScrollView.class)==carousel,"Card choice must retain carousel identity");
            require(carousel.getScrollX()==position && target.hasFocus(),"Card choice must preserve scroll and focus");
            require("card-b".equals(get(activity,"card")),"Card choice must update destination");
            require(((Button)get(activity,"primary")).isEnabled(),"Confirmed card must enable move CTA");
        });
        screenshot("batch-cards.png");
    }
    private void checkResults() throws Exception {
        onMain(()-> {
            Class<?> service=getTargetContext().getClassLoader().loadClass("app.local.extension.curve.BatchGbitService");
            Field running=service.getDeclaredField("running"); running.setAccessible(true);
            boolean previous=running.getBoolean(null);
            try {
            running.setBoolean(null,true);
            JSONObject batch=new JSONObject().put("id","test-batch").put("account","fixture-account").put("status","complete").put("cardName","Test Visa · 5678")
                .put("rows",new JSONArray().put(new JSONObject().put("id","one").put("title","Test shop").put("display","12.50 GBP").put("state","moved")));
            set(activity,"batch",batch); call(activity,"renderProgress");
            TextView outcome=(TextView)((Map)get(activity,"outcomeViews")).get("one");
            outcome.setFocusableInTouchMode(true); require(outcome.requestFocus(),"Outcome must accept focus");
            call(activity,"renderProgress");
            require(((Map)get(activity,"outcomeViews")).get("one")==outcome && outcome.hasFocus(),"Progress refresh must retain outcome view and focus");
            require(Boolean.TRUE.equals(call(activity,"terminalBatch")),"Completed results must stop automatic polling");
            require("New batch".contentEquals(((Button)get(activity,"secondary")).getText()),"Completed results need explicit New batch");
            require(((TextView)get(activity,"description")).getText().toString().contains("complete"),"Completed result must win over service cleanup");
            } finally { running.setBoolean(null,previous); }
        });
        screenshot("batch-progress.png");
    }
    private void checkWakeLock(ClassLoader loader) throws Exception {
        onMain(()-> {
            Service service=(Service)loader.loadClass("app.local.extension.curve.BatchGbitService").getConstructor().newInstance();
            Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class); attach.setAccessible(true); attach.invoke(service,getTargetContext());
            service.onCreate();
            try {
                call(service,"acquireWakeLock");
                PowerManager.WakeLock lock=(PowerManager.WakeLock)get(service,"wakeLock");
                require(lock.isHeld() && Boolean.TRUE.equals(call(service,"canSubmit")),"Batch must hold CPU wake lock during execution");
                set(service,"awakeUntil",SystemClock.elapsedRealtime()-1);
                require(Boolean.FALSE.equals(call(service,"canSubmit")),"Expired batch must not submit another purchase");
                call(service,"releaseWakeLock"); require(!lock.isHeld(),"Completion must release wake lock");
                call(service,"acquireWakeLock"); lock=(PowerManager.WakeLock)get(service,"wakeLock");
                service.onDestroy(); require(!lock.isHeld(),"Destruction must release wake lock");
            } finally { service.onDestroy(); }
        });
    }
    @Override public void onStart() {
        Bundle out=new Bundle();
        try {
            require(args!=null && "morphe_sample_api36".equals(args.getString("avd")) && (Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish")),"Dedicated emulator only");
            require(ready.await(15,TimeUnit.SECONDS),"Curve initialization timed out");
            Context context=getTargetContext(); ClassLoader loader=context.getClassLoader(); Class<?> bridge=loader.loadClass("app.local.extension.curve.BatchCurve");
            Object auth=call(bridge,"resolve","com.curve.data.authentication.domain.usecase.IsUserLoggedInUseCase");
            require(!Boolean.TRUE.equals(auth.getClass().getMethod("invoke").invoke(auth)),"Emulator must be signed out");
            checkDates(loader,bridge); checkCancellation(loader,bridge); checkWakeLock(loader);
            activity=startActivitySync(new Intent().setClassName(context.getPackageName(),"app.local.extension.curve.BatchGbitActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<30;i++) { settle(); AtomicBoolean busy=new AtomicBoolean(); onMain(()->busy.set((Boolean)get(activity,"busy"))); if(!busy.get()) break; }
            onMain(()->require(!(Boolean)get(activity,"busy"),"Signed-out activity initialization did not finish"));
            checkSelection(); checkCards(bridge); checkResults();
            out.putString("stream","PASS Batch native API dates, cancellation, wake lock, stable selection/card controls and completed results\n");
        } catch(Throwable e) { out.putString("stream",android.util.Log.getStackTraceString(e)); finish(0,out); return; }
        finally { if(activity!=null) runOnMainSync(()->activity.finish()); }
        finish(-1,out);
    }
}
