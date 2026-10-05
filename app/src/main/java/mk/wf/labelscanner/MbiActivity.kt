package mk.wf.labelscanner

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class MbiActivity : AppCompatActivity() {
 private val auth by lazy { FirebaseAuth.getInstance() }
 private val db by lazy { FirebaseFirestore.getInstance() }
 private lateinit var root: LinearLayout
 private var docId: String? = null
 private var profile: Map<String, Any> = emptyMap()
 private val bg=Color.rgb(20,23,26); private val panel=Color.rgb(43,48,52)
 private val yellow=Color.rgb(255,193,7); private val steel=Color.rgb(130,138,145)
 private val green=Color.rgb(46,160,86); private val red=Color.rgb(205,67,67)

 override fun onCreate(b:Bundle?){super.onCreate(b);root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,42,36,42);setBackgroundColor(bg)};setContentView(ScrollView(this).apply{addView(root)});if(auth.currentUser==null)login()else load()}
 private fun box(c:Int,r:Float=20f)=GradientDrawable().apply{setColor(c);cornerRadius=r}
 private fun reset(tag:String){root.removeAllViews();root.addView(TextView(this).apply{text="MBI";textSize=38f;setTextColor(yellow);gravity=Gravity.CENTER;setTypeface(typeface,Typeface.BOLD)});root.addView(TextView(this).apply{text="METAL DESIGN";textSize=17f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;letterSpacing=.15f});root.addView(TextView(this).apply{text=tag;textSize=12f;setTextColor(steel);gravity=Gravity.CENTER;setPadding(0,5,0,28)})}
 private fun inp(h:String,p:Boolean=false)=EditText(this).apply{hint=h;setHintTextColor(steel);setTextColor(Color.WHITE);textSize=17f;setPadding(22,10,22,10);background=box(panel);minHeight=60;if(p)inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
 private fun btn(t:String,c:Int=yellow,tc:Int=Color.BLACK,f:()->Unit)=Button(this).apply{text=t;textSize=16f;setTextColor(tc);setTypeface(typeface,Typeface.BOLD);background=box(c);setOnClickListener{f()};layoutParams=LinearLayout.LayoutParams(-1,60).apply{setMargins(0,10,0,10)}}
 private fun title(t:String,s:String){root.addView(TextView(this).apply{text=t;textSize=23f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)});root.addView(TextView(this).apply{text=s;textSize=14f;setTextColor(steel);setPadding(0,4,0,18)})}
 private fun card(t:String)=TextView(this).apply{text=t;textSize=16f;setTextColor(Color.WHITE);setPadding(20,18,20,18);background=box(panel);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,6,0,6)}}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()

 private fun login(){reset("СИГУРНА НАЈАВА");title("Добредојдовте","Најавете се со MBI корисничко име.");val u=inp("Корисничко име или e-mail");val p=inp("Лозинка",true);root.addView(u);root.addView(p);root.addView(btn("НАЈАВА"){val x=u.text.toString().trim();val pw=p.text.toString();if(x.isBlank()||pw.isBlank()){toast("Внеси корисничко име и лозинка.");return@btn};val email=if(x.contains("@"))x else x.lowercase().replace(" ","")+"@mbi.local";auth.signInWithEmailAndPassword(email,pw).addOnSuccessListener{load()}.addOnFailureListener{e->toast("Најавата не успеа: "+(e.localizedMessage?:"провери интернет и лозинка"))}})}
 private fun load(){val uid=auth.currentUser?.uid?:return login();db.collection("employees").whereEqualTo("uid",uid).limit(1).get().addOnSuccessListener{q->if(q.isEmpty){toast("Најавата успеа, но UID нема профил во employees.");auth.signOut();login()}else{docId=q.documents[0].id;profile=q.documents[0].data?:emptyMap();if(profile["isAdmin"]==true)admin()else employee()}}.addOnFailureListener{e->toast("Firestore пристапот е блокиран: "+(e.localizedMessage?:"permission denied"))}}

 private fun employee(){reset("ВРАБОТЕН");title("Здраво, "+(profile["name"]?:"Вработен"),"Избери ја тековната активност.");if(profile["pinHash"]==null)root.addView(btn("ПОСТАВИ PIN",steel,Color.WHITE){setPin()});root.addView(btn("ДОЈДОВ НА РАБОТА",green,Color.WHITE){pin("work_start")});root.addView(btn("ПОЧЕТОК НА ПАУЗА"){pin("break_start")});root.addView(btn("ПРОДОЛЖИ СО РАБОТА",steel,Color.WHITE){pin("break_end")});root.addView(btn("ЗАВРШИ СМЕНА",red,Color.WHITE){pin("work_end")});root.addView(btn("ОДЈАВА",panel,Color.WHITE){auth.signOut();login()})}
 private fun setPin(){val e=inp("Нов PIN",true);e.inputType=InputType.TYPE_CLASS_NUMBER;AlertDialog.Builder(this).setTitle("Постави PIN").setView(e).setPositiveButton("Зачувај"){_,_->val p=e.text.toString();if(p.length<4)toast("PIN мора да има најмалку 4 цифри")else db.collection("employees").document(docId!!).update("pinHash",sha(p)).addOnSuccessListener{load()}}.setNegativeButton("Откажи",null).show()}
 private fun pin(type:String){val expected=profile["pinHash"]?.toString();if(expected==null){toast("Прво постави PIN.");return};val e=inp("PIN",true);e.inputType=InputType.TYPE_CLASS_NUMBER;AlertDialog.Builder(this).setTitle("Потврди PIN").setView(e).setPositiveButton("Потврди"){_,_->if(sha(e.text.toString())!=expected)toast("Погрешен PIN")else record(type)}.setNegativeButton("Откажи",null).show()}
 private fun record(type:String){db.collection("attendance").add(hashMapOf<String,Any>("uid" to auth.currentUser!!.uid,"employeeId" to(docId?:""),"employeeName" to(profile["name"]?.toString()?:""),"type" to type,"timestamp" to FieldValue.serverTimestamp())).addOnSuccessListener{toast("Успешно евидентирано.")}.addOnFailureListener{e->toast("Не може да се запише: "+e.localizedMessage)}}

 private fun admin(){reset("ADMIN CONTROL");title((profile["name"]?.toString()?:"Администратор")+" • ADMIN","Контрола на евиденцијата.");root.addView(btn("КОЈ Е НА РАБОТА",green,Color.WHITE){live()});root.addView(btn("ПОСЛЕДНА ЕВИДЕНЦИЈА"){recent()});root.addView(btn("ВРАБОТЕНИ",steel,Color.WHITE){employees()});root.addView(btn("ОДЈАВА",panel,Color.WHITE){auth.signOut();login()})}
 private fun live(){reset("ADMIN • LIVE");title("Моментална состојба","Последна активност по вработен.");db.collection("attendance").orderBy("timestamp",Query.Direction.DESCENDING).limit(250).get().addOnSuccessListener{q->val seen=mutableSetOf<String>();q.documents.forEach{d->val id=d.getString("employeeId")?:return@forEach;if(seen.add(id)){val st=when(d.getString("type")){"work_start","break_end"->"НА РАБОТА";"break_start"->"НА ПАУЗА";"work_end"->"ЗАВРШЕНА СМЕНА";else->"НЕПОЗНАТО"};root.addView(card((d.getString("employeeName")?:"Вработен")+"\n● "+st))}};root.addView(btn("НАЗАД",panel,Color.WHITE){admin()})}.addOnFailureListener{e->toast("Грешка: "+e.localizedMessage)}}
 private fun employees(){reset("ADMIN • ВРАБОТЕНИ");title("Вработени","Регистрирани профили.");db.collection("employees").get().addOnSuccessListener{q->q.documents.sortedBy{it.getString("name")?:""}.forEach{d->root.addView(card((d.getString("name")?:d.id)+(if(d.getBoolean("isAdmin")==true)" • ADMIN" else "")))};root.addView(btn("НАЗАД",panel,Color.WHITE){admin()})}.addOnFailureListener{e->toast("Грешка: "+e.localizedMessage)}}
 private fun recent(){reset("ADMIN • ИСТОРИЈА");title("Последна евиденција","Најнови 100 активности.");val fmt=SimpleDateFormat("dd.MM.yyyy • HH:mm",Locale.getDefault());db.collection("attendance").orderBy("timestamp",Query.Direction.DESCENDING).limit(100).get().addOnSuccessListener{q->q.documents.forEach{d->val dt=d.getTimestamp("timestamp")?.toDate()?.let{fmt.format(it)}?:"...";root.addView(card((d.getString("employeeName")?:"")+" • "+label(d.getString("type"))+"\n"+dt))};root.addView(btn("НАЗАД",panel,Color.WHITE){admin()})}.addOnFailureListener{e->toast("Грешка: "+e.localizedMessage)}}
 private fun label(t:String?)=when(t){"work_start"->"Дојде на работа";"break_start"->"Почеток на пауза";"break_end"->"Продолжи со работа";"work_end"->"Заврши смена";else->t?:""}
 private fun sha(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){b->"%02x".format(b)}
}