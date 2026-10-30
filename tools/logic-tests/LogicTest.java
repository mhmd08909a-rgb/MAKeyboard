import com.ma.keyboard.*;
import java.util.*;
public class LogicTest {
  static int fails=0, passes=0;
  static void eq(String name, Object a, Object b){ if(!Objects.equals(a,b)){fails++;System.out.println("FAIL "+name+": got "+a+" want "+b);} else passes++; }
  static int del(String s){ return DeleteLogic.charsToDelete(s); }
  static String bs(String s){ return s.substring(0, s.length()-del(s)); }
  static List<List<String>> rows(String... r){ List<List<String>> o=new ArrayList<>(); for(String x:r) o.add(new ArrayList<>(Arrays.asList(x.split(",")))); return o; }
  public static void main(String[] a){
    eq("empty", del(""), 0);
    eq("latin", bs("abc"), "ab");
    eq("persian", bs("سلام"), "سلا");
    eq("digits fa", bs("۱۲۳"), "۱۲");
    eq("space", bs("a "), "a");
    eq("zwnj only", bs("می\u200C"), "می");
    eq("harakat one by one", bs("بَ"), "ب");
    eq("shadda+fatha", bs("بَّ"), "بَ");
    eq("crlf", bs("a\r\n"), "a");
    eq("surrogate emoji", bs("x😀"), "x");
    eq("heart vs16", bs("x❤️"), "x");
    eq("skin tone", bs("x👍🏽"), "x");
    eq("zwj family", bs("x👨‍👩‍👧‍👦"), "x");
    eq("rainbow flag", bs("x🏳️‍🌈"), "x");
    eq("keycap", bs("x1️⃣"), "x");
    eq("flag IR", bs("x🇮🇷"), "x");
    eq("two flags", bs("🇮🇷🇩🇪"), "🇮🇷");
    eq("odd RI", bs("🇮🇷🇩"), "🇮🇷");
    eq("england tag flag", bs("x🏴󠁧󠁢󠁥󠁮󠁧󠁿"), "x");
    eq("emoji then text", bs("😀a"), "😀");
    eq("lone zwj", bs("a\u200D"), "a");
    eq("woman technologist skin", bs("ok👩🏽‍💻"), "ok");
    // repeated backspace eventually empties without splitting surrogates
    String t="سلام 👋🏽 hi 🇮🇷!"; int guard=0; while(!t.isEmpty() && guard++<100){ t=bs(t); for(int i=0;i<t.length();i++){ char c=t.charAt(i); if(Character.isHighSurrogate(c) && (i+1>=t.length()||!Character.isLowSurrogate(t.charAt(i+1)))) {fails++;System.out.println("broken surrogate: "+t);} } }
    eq("drains", t, "");

    // ---- layout
    List<List<String>> base = rows("c:q#0,c:w#0,c:e#0","c:a#0,c:s#0","t:SHIFT#0,c:z#0,t:DEL#0","t:SYM#0,t:SPACE#0,t:ENTER#0");
    Map<String,Integer> home = LayoutModel.baseRows(base);
    List<List<String>> w = LayoutModel.copy(base);
    eq("swap within row", LayoutModel.move(w, home, 0,0, 0,3, 13), true);
    eq("row0 after", w.get(0), Arrays.asList("c:w#0","c:e#0","c:q#0"));
    eq("letter to other row", LayoutModel.move(w, home, 0,2, 1,0, 13), true);
    eq("row1", w.get(1), Arrays.asList("c:q#0","c:a#0","c:s#0"));
    eq("DEL cannot leave row", LayoutModel.move(w, home, 2,2, 0,0, 13), false);
    eq("DEL within row ok", LayoutModel.move(w, home, 2,2, 2,0, 13), true);
    eq("row2", w.get(2), Arrays.asList("t:DEL#0","t:SHIFT#0","c:z#0"));
    eq("letter cannot go to bottom", LayoutModel.move(w, home, 1,0, 3,1, 13), false);
    eq("space reorder in bottom", LayoutModel.move(w, home, 3,1, 3,0, 13), true);
    eq("bottom", w.get(3), Arrays.asList("t:SPACE#0","t:SYM#0","t:ENTER#0"));
    eq("drop on itself", LayoutModel.move(w, home, 3,0, 3,1, 13), false);
    eq("max per row", LayoutModel.move(w, home, 1,0, 0,0, 2), false);
    // empty row protection
    List<List<String>> w2 = rows("c:a#0","c:b#0,c:c#0","t:SPACE#0");
    eq("cannot empty", LayoutModel.move(w2, LayoutModel.baseRows(w2), 0,0, 1,0, 13), false);
    // reconcile: saved has dup, unknown, missing, illegal
    List<List<String>> saved = rows("c:e#0,c:q#0,c:q#0,c:XX#0,t:DEL#0","c:a#0","t:SHIFT#0,c:z#0","t:ENTER#0,t:SPACE#0,t:SYM#0");
    List<List<String>> r = LayoutModel.reconcile(base, saved, 13);
    eq("rec row0", r.get(0), Arrays.asList("c:e#0","c:w#0","c:q#0"));
    eq("rec row1", r.get(1), Arrays.asList("c:a#0","c:s#0"));
    eq("rec row2 DEL restored", r.get(2), Arrays.asList("t:SHIFT#0","c:z#0","t:DEL#0"));
    eq("rec bottom", r.get(3), Arrays.asList("t:ENTER#0","t:SPACE#0","t:SYM#0"));
    int total=0; Set<String> all=new HashSet<>(); for(List<String> x:r){ total+=x.size(); all.addAll(x);} 
    eq("no loss no dup", total, 11); eq("unique", all.size(), 11);
    eq("wrong row count -> default", LayoutModel.reconcile(base, rows("c:q#0"), 13), base);
    eq("null -> default", LayoutModel.reconcile(base, null, 13), base);
    // random fuzz: many random moves never lose/duplicate keys
    Random rnd=new Random(7); List<List<String>> f=LayoutModel.copy(base);
    for(int i=0;i<5000;i++){ int fr=rnd.nextInt(4); if(f.get(fr).isEmpty()) continue; LayoutModel.move(f, home, fr, rnd.nextInt(f.get(fr).size()), rnd.nextInt(4), rnd.nextInt(6), 5); }
    Set<String> fs=new HashSet<>(); int ft=0; boolean emptyRow=false; for(List<String> x:f){ft+=x.size(); fs.addAll(x); if(x.isEmpty()) emptyRow=true;}
    eq("fuzz total", ft, 11); eq("fuzz unique", fs.size(), 11); eq("fuzz no empty row", emptyRow, false);
    eq("fuzz DEL row", f.get(2).contains("t:DEL#0"), true); eq("fuzz bottom intact", new HashSet<>(f.get(3)), new HashSet<>(base.get(3)));
    eq("fuzz reconcile identity", LayoutModel.reconcile(base, f, 5), f);

    // ---- fit
    eq("fit no limit", FitMath.fitRowHeight(4, 60f, 0f, 8f, 28f), 60f);
    eq("fit shrink", FitMath.fitRowHeight(5, 100f, 408f, 8f, 28f), 80f);
    eq("fit ok", FitMath.fitRowHeight(4, 50f, 1000f, 8f, 28f), 50f);
    float[] c = FitMath.calcPanel(250f, 110f, 5, 54f, 36f, 700f);
    eq("calc all rows inside", c[0] >= 110f + 5*c[1] - 0.01f, true); eq("calc row >= min", c[1] >= 36f, true);
    float[] c2 = FitMath.calcPanel(250f, 110f, 5, 54f, 36f, 260f);   // old bug: fixed 260 px panel
    eq("calc tight inside", 110f + 5*c2[1] <= c2[0] + 0.01f, true); eq("calc tight panel", c2[0] <= 260f, true);
    System.out.println("passes="+passes+" fails="+fails);
    if(fails>0) System.exit(1);
  }
}
