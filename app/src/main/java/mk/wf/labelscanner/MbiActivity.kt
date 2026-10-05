package mk.wf.labelscanner
import android.graphics.Color
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

class MbiActivity:AppCompatActivity(){
 private val auth by lazy{FirebaseAuth.getInstance()}
 private val db by lazy{FirebaseFirestore.getInstance()}
 private lateinit var root:LinearLayout
 private var docId:String?=null
 private var profile:Map<String,Any> = emptyMap()
 override fun onCreate(b:Bundle?){super.onCreate(b);root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,48,36,36);setBackgroundColor(Color.rgb(18,18,18))};setContentView(ScrollView(this).apply{addView(root)});if(auth.currentUser==null)login()else load()}
 private fun reset(){root.removeAllViews();root.addView(TextView(this).apply{text="MBI • METAL DESIGN";textSize=27f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(8,20,8,24)})}
 private fun inp(h:String,p:Boolean=false)=EditText(this).apply{hint=h;setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);if(p)inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
 private fun btn(t:String,f:()->Unit)=Button(this).apply{text=t;setOnClickListener{f()}}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
 private fun login(){reset();val u=inp("Корисничко име или e-mail");val p=inp("Лозинка",true);root.addView(u);root.addView(p);root.addView(btn("НАЈАВА"){val x=u.text.toString().trim();val email=if(x.contains("@"))x else x.lowercase().replace(" ","")+"@mbi.local";auth.signInWithEmailAndPassword(email,p.text.toString()).addOnSuccessListener{load()}.addOnFailureListener{e->toast("Неуспешна најава: "+e.localizedMessage)}})}
 private fun load(){val uid=auth.currentUser?.uid?:return login();db.collection("employees").whereEqualTo("uid",uid).limit(1).get().addOnSuccessListener{q->if(q.isEmpty){toast("Корисникот нема MBI профил.");auth.signOut();login()}else{docId=q.documents[0].id;profile=q.documents[0].data?:emptyMap();if(profile["isAdmin"]==true)admin()else employee()}}.addOnFailureListener{e->toast("Нема пристап до базата: "+e.localizedMessage)}}
 private fun employee(){reset();root.addView(TextView(this).apply{text="Здраво, "+(profile["name"]?:"Вработен");textSize=21f;setTextColor(Color.WHITE)});if(profile["pinHash"]==null)root.addView(btn("ПОСТАВИ PIN"){setPin()});root.addView(btn("ПОЧЕТОК НА РАБОТА"){pin("work_start")});root.addView(btn("ПОЧЕТОК НА ПАУЗА"){pin("break_start")});root.addView(btn("КРАЈ НА ПАУЗА"){pin("break_end")});root.addView(btn("КРАЈ НА РАБОТА"){pin("work_end")});root.addView(btn("ОДЈАВА"){auth.signOut();login()})}
 private fun setPin(){val e=inp("Нов PIN",true);e.inputType=2;AlertDialog.Builder(this).setTitle("Постави PIN").setView(e).setPositiveButton("Зачувај"){_,_->val p=e.text.toString();if(p.length<4)toast("PIN мора да има најмалку 4 цифри")else db.collection("employees").document(docId!!).update("pinHash",sha(p)).addOnSuccessListener{load()}}.setNegativeButton("Откажи",null).show()}
 private fun pin(type:String){val expected=profile["pinHash"]?.toString();if(expected==null){toast("Прво постави PIN.");return};val e=inp("PIN",true);e.inputType=2;AlertDialog.Builder(this).setTitle("Потврди PIN").setView(e).setPositiveButton("Потврди"){_,_->if(sha(e.text.toString())!=expected)toast("Погрешен PIN")else record(type)}.setNegativeButton("Откажи",null).show()}
 private fun record(type:String){db.collection("attendance").add(hashMapOf<String,Any>("uid" to auth.currentUser!!.uid,"employeeId" to(docId?:""),"employeeName" to(profile["name"]?.toString()?:""),"type" to type,"timestamp" to FieldValue.serverTimestamp())).addOnSuccessListener{toast("Успешно евидентирано.")}.addOnFailureListener{e->toast("Грешка: "+e.localizedMessage)}}
 private fun admin(){reset();root.addView(TextView(this).apply{text="Vele • Администратор";textSize=22f;setTextColor(Color.WHITE)});root.addView(btn("ПОСЛЕДНА ЕВИДЕНЦИЈА"){recent()});root.addView(btn("ОДЈАВА"){auth.signOut();login()})}
 private fun recent(){reset();root.addView(TextView(this).apply{text="Последна евиденција";textSize=20f;setTextColor(Color.WHITE)});db.collection("attendance").orderBy("timestamp",Query.Direction.DESCENDING).limit(100).get().addOnSuccessListener{q->q.documents.forEach{d->root.addView(TextView(this).apply{text=(d.getString("employeeName")?:"")+" • "+label(d.getString("type"))+"\n"+(d.getTimestamp("timestamp")?.toDate()?.toString()?:"...");setTextColor(Color.WHITE);textSize=16f;setPadding(5,16,5,16)})};root.addView(btn("НАЗАД"){admin()})}.addOnFailureListener{e->toast("Грешка: "+e.localizedMessage)}}
 private fun label(t:String?)=when(t){"work_start"->"Почеток на работа";"break_start"->"Почеток на пауза";"break_end"->"Крај на пауза";"work_end"->"Крај на работа";else->t?:""}
 private fun sha(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){b->"%02x".format(b)}
}