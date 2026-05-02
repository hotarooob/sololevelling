package com.upfunding.solorift;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(new SoloRiftView(this));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    static class SoloRiftView extends View {
        private static final int MENU = 0;
        private static final int BATTLE = 1;
        private static final int WARRIOR = 2;
        private static final int SHOP = 3;
        private static final int BAG = 4;
        private static final int SCREEN_MISSIONS = 5;
        private static final int RESULT = 6;
        private static final int HISTORY = 7;

        private static final int ACT_START_BATTLE = 100;
        private static final int ACT_WARRIOR = 101;
        private static final int ACT_SHOP = 102;
        private static final int ACT_BAG = 103;
        private static final int ACT_MISSIONS = 104;
        private static final int ACT_HISTORY = 105;
        private static final int ACT_BACK = 106;
        private static final int ACT_RESET = 107;
        private static final int ACT_ATTACK_HEAVY = 201;
        private static final int ACT_ATTACK_FAST = 202;
        private static final int ACT_ATTACK_FOCUS = 203;
        private static final int ACT_ATTACK_GUARD = 204;
        private static final int ACT_BUY = 301;
        private static final int ACT_EQUIP = 401;
        private static final int ACT_SELL = 402;
        private static final int ACT_UPGRADE = 501;

        private final Activity activity;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final ArrayList<Button> buttons = new ArrayList<>();
        private final ArrayList<Particle> particles = new ArrayList<>();
        private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 45);
        private final Vibrator vibrator;
        private final SharedPreferences sp;

        private int w, h;
        private int screen = MENU;
        private long lastNanos;
        private float pulse;
        private String toast = "";
        private float toastTime = 0f;
        private Profile profile;
        private Battle battle;
        private StoreOffer[] offers = new StoreOffer[0];
        private String resultTitle = "";
        private String resultBody = "";

        private final int bgTop = Color.rgb(18, 19, 34);
        private final int bgBottom = Color.rgb(63, 35, 98);
        private final int card = Color.argb(210, 255, 255, 255);
        private final int cardDark = Color.argb(185, 18, 20, 38);
        private final int gold = Color.rgb(255, 203, 86);
        private final int purple = Color.rgb(148, 104, 255);
        private final int red = Color.rgb(255, 89, 109);
        private final int green = Color.rgb(105, 222, 155);
        private final int cyan = Color.rgb(112, 211, 255);
        private final int ink = Color.rgb(43, 44, 55);

        SoloRiftView(Activity activity) {
            super(activity);
            this.activity = activity;
            this.sp = activity.getSharedPreferences("solo_rift_save", Context.MODE_PRIVATE);
            this.vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            this.profile = Profile.load(sp);
            this.offers = createStoreOffers();
            setFocusable(true);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldw, int oldh) {
            super.onSizeChanged(width, height, oldw, oldh);
            w = Math.max(1, width);
            h = Math.max(1, height);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            long now = System.nanoTime();
            float dt = 0f;
            if (lastNanos != 0L) dt = Math.min(0.035f, (now - lastNanos) / 1_000_000_000f);
            lastNanos = now;
            pulse += dt;
            if (toastTime > 0) toastTime -= dt;
            updateParticles(dt);

            buttons.clear();
            drawBackground(c);
            drawStars(c);
            drawParticles(c);

            if (screen == MENU) drawMenu(c);
            else if (screen == BATTLE) drawBattle(c);
            else if (screen == WARRIOR) drawWarrior(c);
            else if (screen == SHOP) drawShop(c);
            else if (screen == BAG) drawBag(c);
            else if (screen == SCREEN_MISSIONS) drawMissions(c);
            else if (screen == HISTORY) drawHistory(c);
            else if (screen == RESULT) drawResult(c);

            drawToast(c);
            postInvalidateOnAnimation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float x = event.getX();
            float y = event.getY();
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Button b = buttons.get(i);
                if (b.rect.contains(x, y)) {
                    handleAction(b.action, b.index);
                    return true;
                }
            }
            return true;
        }

        private void handleAction(int action, int index) {
            ping(18);
            if (action == ACT_BACK) {
                screen = MENU;
                battle = null;
                return;
            }
            if (action == ACT_START_BATTLE) {
                startBattle();
                return;
            }
            if (action == ACT_WARRIOR) { screen = WARRIOR; return; }
            if (action == ACT_SHOP) { offers = createStoreOffers(); screen = SHOP; return; }
            if (action == ACT_BAG) { screen = BAG; return; }
            if (action == ACT_MISSIONS) { screen = SCREEN_MISSIONS; return; }
            if (action == ACT_HISTORY) { screen = HISTORY; return; }
            if (action == ACT_RESET) {
                profile = new Profile();
                profile.save(sp);
                offers = createStoreOffers();
                toast("بدأت رحلة جديدة");
                return;
            }
            if (action == ACT_ATTACK_HEAVY || action == ACT_ATTACK_FAST || action == ACT_ATTACK_FOCUS || action == ACT_ATTACK_GUARD) {
                runBattleTurn(action);
                return;
            }
            if (action == ACT_BUY) {
                buyOffer(index);
                return;
            }
            if (action == ACT_EQUIP) {
                equipInventory(index);
                return;
            }
            if (action == ACT_SELL) {
                sellInventory(index);
                return;
            }
            if (action == ACT_UPGRADE) {
                upgradeStat(index);
            }
        }

        private void startBattle() {
            battle = new Battle();
            battle.boss = generateBoss();
            Stats s = getStats(profile);
            battle.playerMaxHp = s.maxHp;
            battle.playerHp = s.maxHp;
            battle.round = 1;
            battle.maxRounds = 5;
            battle.lastLog = "ظهرت بوابة جديدة. اختر أسلوب الضربة ودمر الساحة.";
            screen = BATTLE;
            burst(w * 0.5f, h * 0.38f, purple, 36);
            tone.startTone(ToneGenerator.TONE_PROP_PROMPT, 90);
        }

        private void runBattleTurn(int mode) {
            if (battle == null || battle.finished) return;
            Stats stats = getStats(profile);
            Boss boss = battle.boss;
            int round = battle.round;
            String modeName = "هجوم";
            float damageMul = 1f;
            float armorMul = 1f;
            float takenMul = 1f;
            int bonusCrit = 0;
            int statBonus = 0;

            if (mode == ACT_ATTACK_HEAVY) { modeName = "ضربة ساحقة"; damageMul = 1.28f; armorMul = 1.15f; takenMul = 1.05f; statBonus = stats.strength / 4; }
            if (mode == ACT_ATTACK_FAST) { modeName = "اندفاع سريع"; damageMul = 0.92f; armorMul = 0.82f; takenMul = 0.88f; bonusCrit = 14; statBonus = stats.speed / 3; }
            if (mode == ACT_ATTACK_FOCUS) { modeName = "قراءة مركزة"; damageMul = 1.05f; armorMul = 1.55f; takenMul = 0.95f; bonusCrit = 6; statBonus = stats.intelligence / 3; }
            if (mode == ACT_ATTACK_GUARD) { modeName = "هجوم دفاعي"; damageMul = 0.82f; armorMul = 0.95f; takenMul = 0.55f; statBonus = stats.defense / 2; }

            int baseDamage = Math.max(4, stats.attack + stats.strength + statBonus + rand(-9, 14));
            baseDamage += (int) (stats.speed * 0.25f + stats.intelligence * 0.18f + stats.pierce * 1.2f);
            int critChance = clampInt(7 + stats.speed / 8 + stats.intelligence / 15 + bonusCrit, 5, 42);
            boolean crit = rand(1, 100) <= critChance;
            if (crit) baseDamage = (int) (baseDamage * 1.55f);
            int armorBlock = Math.max(0, (int) (boss.armor * 0.18f) - stats.pierce);
            int rawDamage = Math.max(1, (int) ((baseDamage - armorBlock) * damageMul));

            Ability ability = boss.ability;
            ArrayList<String> notes = new ArrayList<>();
            if (crit) notes.add("ضربة حرجة لمعت مثل البرق");
            if (ability.id.equals("shadow_step") && stats.speed < boss.speed) {
                rawDamage = (int) (rawDamage * 0.78f);
                notes.add("خطوة الظل سرقت جزءاً من ضررك");
            }
            if ((ability.id.equals("cunning_mind") || ability.id.equals("mana_shell")) && stats.intelligence < boss.cunning) {
                rawDamage = (int) (rawDamage * 0.82f);
                notes.add("دهاء الوحش غيّر مسار الضربة");
            }
            if (ability.id.equals("stone_skin") || ability.id.equals("titan_guard")) {
                rawDamage -= Math.max(3, boss.defense / 6);
                notes.add("جلد الوحش الصلب امتص ضربة مباشرة");
            }
            if (ability.id.equals("berserk") && round >= 3) notes.add("جنون الوحش يتصاعد كل جولة");

            rawDamage = Math.max(1, rawDamage);
            int armorDamage = Math.min(boss.armor, Math.max(0, (int) (rawDamage * 0.36f * armorMul + stats.pierce)));
            boss.armor = Math.max(0, boss.armor - armorDamage);
            int hpDamage = Math.max(1, rawDamage - (int) (armorDamage * 0.38f));
            if (stats.poison > 0) {
                int poison = Math.max(2, stats.poison + stats.intelligence / 18 + rand(0, 5));
                hpDamage += poison;
                notes.add("سم السلاح أضاف " + poison + " ضرر");
            }
            boss.hp = Math.max(0, boss.hp - hpDamage);
            profile.highestDamage = Math.max(profile.highestDamage, hpDamage);

            int taken = 0;
            if (boss.hp > 0) {
                int bossDamage = Math.max(4, boss.offense + rand(-8, 13));
                if (ability.id.equals("brutal_charge")) bossDamage = (int) (bossDamage * 1.18f);
                if (ability.id.equals("berserk")) bossDamage = (int) (bossDamage * (1f + round * 0.11f));
                if (ability.id.equals("ancient_curse")) bossDamage = (int) (bossDamage * 1.15f);
                boolean magic = ability.tags.contains("magic") || ability.tags.contains("curse");
                int block = stats.defense + (magic ? stats.magicShield : 0) + rand(0, 12);
                taken = Math.max(1, (int) ((bossDamage - block * 0.62f) * takenMul));
                battle.playerHp = Math.max(0, battle.playerHp - taken);
                if (ability.id.equals("venom_bite")) {
                    int venom = Math.max(1, boss.offense / 15);
                    battle.playerHp = Math.max(0, battle.playerHp - venom);
                    taken += venom;
                    notes.add("السم ترك أثراً إضافياً");
                }
                if (ability.id.equals("void_hunger") && round >= 3) {
                    int drain = Math.max(2, battle.playerMaxHp / 18);
                    battle.playerHp = Math.max(0, battle.playerHp - drain);
                    taken += drain;
                    notes.add("جوع الفراغ امتص طاقتك");
                }
            }

            degradeGear(stats);
            StringBuilder log = new StringBuilder();
            log.append(modeName).append(" | ضرر ").append(hpDamage).append(" | كسر درع ").append(armorDamage);
            if (taken > 0) log.append(" | تلقيت ").append(taken);
            for (String n : notes) log.append("\n• ").append(n);
            battle.lastLog = log.toString();
            burst(w * 0.70f, h * 0.32f, crit ? gold : red, crit ? 42 : 24);
            tone.startTone(crit ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_BEEP, 60);

            if (boss.hp <= 0) {
                winBattle();
                return;
            }
            if (battle.playerHp <= 0 || battle.round >= battle.maxRounds) {
                loseBattle();
                return;
            }
            battle.round++;
            profile.save(sp);
        }

        private void winBattle() {
            Boss boss = battle.boss;
            profile.totalKills++;
            profile.totalWins++;
            profile.streak++;
            profile.bestStreak = Math.max(profile.bestStreak, profile.streak);
            int rarityIndex = rarityIndex(boss.rarity.key);
            if (rarityIndex >= 0) profile.killsByRarity[rarityIndex]++;
            if (battle.round <= 2) profile.fastWins++;
            if (boss.ability.tags.contains("armor") || boss.maxArmor >= 65) profile.armoredKills++;
            if (boss.ability.tags.contains("cunning") || boss.ability.tags.contains("magic") || boss.ability.tags.contains("curse")) profile.cunningKills++;
            if (boss.ability.tags.contains("speed")) profile.swiftKills++;
            if (boss.ability.tags.contains("poison")) profile.poisonKills++;
            if (boss.ability.tags.contains("magic") || boss.ability.tags.contains("curse")) profile.magicKills++;
            if (boss.ability.tags.contains("fear")) profile.fearKills++;
            int coins = rand(boss.rarity.rewardMin, boss.rarity.rewardMax);
            profile.coins += coins;
            ArrayList<String> completed = advanceMissions();
            String lootLine = maybeDropLoot(boss.rarity);
            profile.save(sp);
            resultTitle = "انتصار بوابة";
            resultBody = "هزمت " + boss.name + "\nالندرة: " + boss.rarity.shortName + "\nربحت: " + coins + " عملة\n" + lootLine;
            if (!completed.isEmpty()) {
                resultBody += "\n\nمهام مكتملة:";
                for (String s : completed) resultBody += "\n• " + s;
            }
            battle.finished = true;
            screen = RESULT;
            burst(w * 0.5f, h * 0.45f, gold, 90);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 220);
            ping(70);
        }

        private void loseBattle() {
            profile.totalLosses++;
            profile.streak = 0;
            profile.save(sp);
            resultTitle = "فشلت البوابة";
            resultBody = "الوحش بقي واقفاً: " + battle.boss.name + "\nHP المتبقي: " + battle.boss.hp + "/" + battle.boss.maxHp + "\nطور المحارب أو اشتر عتاد أقوى.";
            battle.finished = true;
            screen = RESULT;
            burst(w * 0.5f, h * 0.45f, red, 55);
            tone.startTone(ToneGenerator.TONE_PROP_NACK, 180);
            ping(45);
        }

        private void buyOffer(int index) {
            if (index < 0 || index >= offers.length) return;
            StoreOffer offer = offers[index];
            Item item = offer.item;
            if (profile.coins < offer.price) {
                toast("رصيدك غير كافٍ");
                tone.startTone(ToneGenerator.TONE_PROP_NACK, 100);
                return;
            }
            profile.coins -= offer.price;
            profile.inventory.put(item.id, profile.inventory.getOrDefault(item.id, 0) + 1);
            profile.save(sp);
            toast("تم شراء " + item.name);
            burst(w * 0.5f, h * 0.55f, gold, 28);
        }

        private void equipInventory(int index) {
            List<Item> inv = inventoryList();
            if (index < 0 || index >= inv.size()) return;
            Item item = inv.get(index);
            if (!item.slot.equals("weapon") && !item.slot.equals("armor")) {
                toast("هذا العنصر مستهلك، سيُستخدم تلقائياً في القتال القادم لاحقاً");
                return;
            }
            if (item.slot.equals("weapon")) {
                profile.weaponId = item.id;
                if (profile.weaponDur <= 0) profile.weaponDur = item.durability;
            } else {
                profile.armorId = item.id;
                if (profile.armorDur <= 0) profile.armorDur = item.durability;
            }
            profile.save(sp);
            toast("تم تجهيز " + item.name);
        }

        private void sellInventory(int index) {
            List<Item> inv = inventoryList();
            if (index < 0 || index >= inv.size()) return;
            Item item = inv.get(index);
            int count = profile.inventory.getOrDefault(item.id, 0);
            if (count <= 0) return;
            if (item.id.equals(profile.weaponId) || item.id.equals(profile.armorId)) {
                toast("لا تبيع عتاد مجهز. جهز غيره أولاً.");
                return;
            }
            if (count == 1) profile.inventory.remove(item.id); else profile.inventory.put(item.id, count - 1);
            int gain = Math.max(1, item.price * 55 / 100);
            profile.coins += gain;
            profile.save(sp);
            toast("بعت " + item.name + " وربحت " + gain);
        }

        private void upgradeStat(int index) {
            if (profile.points <= 0) { toast("لا توجد نقاط تطوير"); return; }
            if (index == 0) profile.strength++;
            else if (index == 1) profile.intelligence++;
            else if (index == 2) profile.speed++;
            else if (index == 3) profile.defense++;
            profile.points--;
            profile.save(sp);
            toast("تم التطوير. المتبقي: " + profile.points);
            burst(w * 0.5f, h * 0.5f, cyan, 20);
        }

        private void degradeGear(Stats stats) {
            if (stats.weapon != null && stats.weapon.durability > 0) {
                profile.weaponDur = Math.max(0, profile.weaponDur - rand(2, 5));
                if (profile.weaponDur == 0) toast("انكسر السلاح: " + stats.weapon.name);
            }
            if (stats.armor != null && stats.armor.durability > 0) {
                profile.armorDur = Math.max(0, profile.armorDur - rand(1, 4));
                if (profile.armorDur == 0) toast("انكسر الدرع: " + stats.armor.name);
            }
        }

        private String maybeDropLoot(Rarity rarity) {
            int chance = rarity.key.equals("legendary") ? 38 : rarity.key.equals("elite") ? 28 : rarity.key.equals("alpha") ? 18 : 10;
            if (rand(1, 100) > chance) return "الغنيمة: لا شيء هذه المرة";
            ArrayList<Item> pool = new ArrayList<>();
            for (Item item : CATALOG) {
                if (rarity.key.equals("legendary") || item.rarity.key.equals(rarity.key) || item.rarity.key.equals("beta")) pool.add(item);
            }
            Item item = pool.get(random.nextInt(pool.size()));
            profile.inventory.put(item.id, profile.inventory.getOrDefault(item.id, 0) + 1);
            return "الغنيمة: " + item.rarity.icon + " " + item.name;
        }

        private ArrayList<String> advanceMissions() {
            ArrayList<String> done = new ArrayList<>();
            int guard = 0;
            while (profile.missionIndex < MISSION_LIST.length && guard++ < 4) {
                Mission m = MISSION_LIST[profile.missionIndex];
                if (!isMissionComplete(m)) break;
                profile.points += m.points;
                done.add(m.title + " +" + m.points + " نقاط تطوير");
                if (profile.missionIndex < MISSION_LIST.length - 1) profile.missionIndex++; else break;
            }
            return done;
        }

        private boolean isMissionComplete(Mission m) {
            for (Req r : m.reqs) {
                if (getReqValue(r.key) < r.need) return false;
            }
            return true;
        }

        private int getReqValue(String key) {
            if (key.equals("total")) return profile.totalKills;
            if (key.equals("fastWins")) return profile.fastWins;
            if (key.equals("armored")) return profile.armoredKills;
            if (key.equals("cunning")) return profile.cunningKills;
            if (key.equals("swift")) return profile.swiftKills;
            if (key.equals("poison")) return profile.poisonKills;
            if (key.equals("magic")) return profile.magicKills;
            if (key.equals("fear")) return profile.fearKills;
            int idx = rarityIndex(key);
            return idx >= 0 ? profile.killsByRarity[idx] : 0;
        }

        private Stats getStats(Profile pr) {
            Stats s = new Stats();
            Item weapon = itemById(pr.weaponId);
            Item armor = itemById(pr.armorId);
            if (weapon != null && pr.weaponDur <= 0) weapon = null;
            if (armor != null && pr.armorDur <= 0) armor = null;
            s.weapon = weapon;
            s.armor = armor;
            s.strength = pr.strength;
            s.intelligence = pr.intelligence;
            s.speed = pr.speed;
            s.defense = pr.defense;
            s.maxHp = 140 + pr.defense * 10 + pr.strength * 4 + (armor == null ? 0 : armor.hp);
            s.attack = 18 + pr.strength * 5 + (weapon == null ? 0 : weapon.attack) + ((weapon == null ? 0 : weapon.pierce) / 2);
            s.intelligence = 9 + pr.intelligence * 3 + (weapon == null ? 0 : weapon.intelligence) + (armor == null ? 0 : armor.intelligence);
            s.speed = 9 + pr.speed * 3 + (weapon == null ? 0 : weapon.speed) + (armor == null ? 0 : armor.speed);
            s.defense = 10 + pr.defense * 5 + (armor == null ? 0 : armor.defense) + (weapon == null ? 0 : weapon.defense);
            s.magicShield = armor == null ? 0 : armor.magicShield;
            s.poison = weapon == null ? 0 : weapon.poison;
            s.pierce = weapon == null ? 0 : weapon.pierce;
            return s;
        }

        private int playerPower() {
            Stats s = getStats(profile);
            int attrs = (profile.strength + profile.intelligence + profile.speed + profile.defense) * 3;
            int gear = (s.attack + s.defense + s.speed + s.intelligence + s.magicShield) / 4;
            int mission = Math.min(90, profile.missionIndex * 2);
            int kills = Math.min(90, profile.totalKills / 3);
            return Math.max(10, attrs + gear + mission + kills);
        }

        private Boss generateBoss() {
            int power = playerPower();
            Rarity rarity = chooseRarity(power);
            ArrayList<Ability> abilityPool = new ArrayList<>();
            for (Ability a : ABILITIES) if (rarityRank(rarity.key) >= rarityRank(a.minRarity)) abilityPool.add(a);
            Ability ability = abilityPool.get(random.nextInt(abilityPool.size()));
            String name = MONSTER_NAMES[rarityIndex(rarity.key)][random.nextInt(MONSTER_NAMES[rarityIndex(rarity.key)].length)];
            float rp = rarity.power;
            Boss b = new Boss();
            b.name = name;
            b.rarity = rarity;
            b.ability = ability;
            b.maxHp = Math.max(55, (int) (rarity.baseHp + power * (0.82f * rp) + rand(-10, 22) * rp));
            b.hp = b.maxHp;
            b.maxArmor = Math.max(0, (int) (rarity.baseArmor + power * (0.20f * rp) + rand(-5, 14)));
            b.armor = b.maxArmor;
            b.offense = Math.max(10, (int) (16 * rp + power * (0.36f * rp) + rand(0, 14)));
            b.cunning = Math.max(6, (int) (10 * rp + power * (0.25f * rp) + rand(0, 12)));
            b.speed = Math.max(6, (int) (10 * rp + power * (0.23f * rp) + rand(0, 12)));
            b.defense = Math.max(6, (int) (9 * rp + power * (0.24f * rp) + rand(0, 12)));
            return b;
        }

        private Rarity chooseRarity(int power) {
            int beta = power > 90 ? 44 : 62;
            int alpha = power < 20 ? 20 : 29;
            int elite = power < 30 ? 5 : power > 85 ? 18 : 10;
            int legendary = power < 45 ? 1 : power < 95 ? 4 : 9;
            int total = beta + alpha + elite + legendary;
            int roll = rand(1, total);
            if ((roll -= beta) <= 0) return RARITIES[0];
            if ((roll -= alpha) <= 0) return RARITIES[1];
            if ((roll -= elite) <= 0) return RARITIES[2];
            return RARITIES[3];
        }

        private StoreOffer[] createStoreOffers() {
            ArrayList<StoreOffer> out = new ArrayList<>();
            int power = playerPower();
            ArrayList<Item> pool = new ArrayList<>();
            for (Item item : CATALOG) {
                if (item.rarity.key.equals("legendary") && power < 75 && random.nextInt(100) > 10) continue;
                if (item.rarity.key.equals("elite") && power < 35 && random.nextInt(100) > 35) continue;
                pool.add(item);
            }
            while (out.size() < 6 && !pool.isEmpty()) {
                Item item = pool.remove(random.nextInt(pool.size()));
                int price = Math.max(1, (int) (item.price * (0.88f + random.nextFloat() * 0.24f)));
                out.add(new StoreOffer(item, price));
            }
            return out.toArray(new StoreOffer[0]);
        }

        private List<Item> inventoryList() {
            ArrayList<Item> list = new ArrayList<>();
            for (String id : profile.inventory.keySet()) {
                Item item = itemById(id);
                if (item != null && profile.inventory.getOrDefault(id, 0) > 0) list.add(item);
            }
            return list;
        }

        private void drawBackground(Canvas c) {
            p.setShader(new LinearGradient(0, 0, 0, h, bgTop, bgBottom, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            p.setColor(Color.argb(60, 255, 255, 255));
            for (int i = 0; i < 7; i++) {
                float x = (i * 163 + 41) % Math.max(1, w);
                float y = (i * 239 + 95) % Math.max(1, h);
                c.drawCircle(x, y, w * (0.05f + (i % 3) * 0.025f), p);
            }
        }

        private void drawStars(Canvas c) {
            p.setColor(Color.argb(105, 255, 255, 255));
            for (int i = 0; i < 42; i++) {
                float x = ((i * 97) % Math.max(1, w));
                float y = 40 + ((i * 173) % Math.max(1, h - 80));
                float r = 1.5f + (i % 4);
                c.drawCircle(x, y, r, p);
            }
        }

        private void drawMenu(Canvas c) {
            drawTitle(c, "SOLO RIFT", "بوابات يوتا: وحوش، عتاد، مهام، ودمار نظيف");
            float y = h * 0.28f;
            drawHeroCard(c, y);
            addButton(c, w * 0.10f, h * 0.55f, w * 0.90f, h * 0.62f, "⚔️ افتح بوابة قتال", ACT_START_BATTLE, 0, purple);
            addButton(c, w * 0.10f, h * 0.64f, w * 0.47f, h * 0.71f, "المحارب", ACT_WARRIOR, 0, cyan);
            addButton(c, w * 0.53f, h * 0.64f, w * 0.90f, h * 0.71f, "المتجر", ACT_SHOP, 0, gold);
            addButton(c, w * 0.10f, h * 0.73f, w * 0.47f, h * 0.80f, "الحقيبة", ACT_BAG, 0, green);
            addButton(c, w * 0.53f, h * 0.73f, w * 0.90f, h * 0.80f, "المهام", ACT_MISSIONS, 0, red);
            addButton(c, w * 0.10f, h * 0.82f, w * 0.47f, h * 0.89f, "التاريخ", ACT_HISTORY, 0, purple);
            addButton(c, w * 0.53f, h * 0.82f, w * 0.90f, h * 0.89f, "رحلة جديدة", ACT_RESET, 0, Color.rgb(120, 126, 143));
        }

        private void drawHeroCard(Canvas c, float top) {
            RectF r = new RectF(w * 0.08f, top, w * 0.92f, top + h * 0.22f);
            drawCard(c, r, cardDark);
            Stats s = getStats(profile);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            p.setTextSize(sp(22));
            p.setColor(Color.WHITE);
            c.drawText("محارب البوابة", r.right - 28, r.top + 46, p);
            p.setTextSize(sp(15));
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("عملات: " + profile.coins + "   نقاط تطوير: " + profile.points, r.right - 28, r.top + 82, p);
            c.drawText("HP " + s.maxHp + " | ATK " + s.attack + " | DEF " + s.defense + " | SPD " + s.speed, r.right - 28, r.top + 116, p);
            c.drawText("انتصارات: " + profile.totalWins + " | قتلات: " + profile.totalKills + " | سلسلة: " + profile.streak, r.right - 28, r.top + 150, p);
            drawWarriorIcon(c, r.left + 82, r.top + 105, 54, gold);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawBattle(Canvas c) {
            if (battle == null) { screen = MENU; return; }
            Boss b = battle.boss;
            RectF top = new RectF(w * 0.05f, h * 0.035f, w * 0.95f, h * 0.17f);
            drawCard(c, top, cardDark);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(sp(18));
            p.setColor(Color.WHITE);
            c.drawText("الجولة " + battle.round + " / " + battle.maxRounds, top.right - 22, top.top + 38, p);
            p.setTextSize(sp(14));
            c.drawText("الوحش: " + b.name + "  " + b.rarity.icon + " " + b.rarity.shortName, top.right - 22, top.top + 70, p);
            c.drawText("القدرة: " + b.ability.name, top.right - 22, top.top + 102, p);
            addButton(c, top.left + 14, top.top + 22, top.left + 96, top.top + 72, "خروج", ACT_BACK, 0, Color.rgb(115, 120, 140));

            drawMonster(c, w * 0.70f, h * 0.34f, Math.min(w, h) * 0.115f, b);
            drawWarriorIcon(c, w * 0.28f, h * 0.42f, Math.min(w, h) * 0.09f, gold);
            drawBars(c);

            RectF log = new RectF(w * 0.06f, h * 0.57f, w * 0.94f, h * 0.72f);
            drawCard(c, log, card);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            p.setTextSize(sp(14));
            p.setColor(ink);
            drawMultiline(c, battle.lastLog, log.right - 20, log.top + 32, sp(14), Paint.Align.RIGHT, ink, 4);

            float y1 = h * 0.75f;
            float y2 = h * 0.84f;
            addButton(c, w * 0.06f, y1, w * 0.47f, y1 + h * 0.07f, "ضربة ساحقة", ACT_ATTACK_HEAVY, 0, red);
            addButton(c, w * 0.53f, y1, w * 0.94f, y1 + h * 0.07f, "اندفاع سريع", ACT_ATTACK_FAST, 0, cyan);
            addButton(c, w * 0.06f, y2, w * 0.47f, y2 + h * 0.07f, "قراءة مركزة", ACT_ATTACK_FOCUS, 0, purple);
            addButton(c, w * 0.53f, y2, w * 0.94f, y2 + h * 0.07f, "هجوم دفاعي", ACT_ATTACK_GUARD, 0, green);
        }

        private void drawBars(Canvas c) {
            Boss b = battle.boss;
            drawStatusBar(c, w * 0.49f, h * 0.47f, w * 0.44f, h * 0.024f, b.hp, b.maxHp, red, "HP الوحش");
            drawStatusBar(c, w * 0.49f, h * 0.505f, w * 0.44f, h * 0.018f, b.armor, Math.max(1, b.maxArmor), purple, "درع");
            drawStatusBar(c, w * 0.07f, h * 0.51f, w * 0.42f, h * 0.024f, battle.playerHp, battle.playerMaxHp, green, "HP المحارب");
        }

        private void drawStatusBar(Canvas c, float x, float y, float width, float height, int value, int max, int color, String label) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(95, 0, 0, 0));
            RectF bg = new RectF(x, y, x + width, y + height);
            c.drawRoundRect(bg, height / 2, height / 2, p);
            float ratio = clamp01(value / (float) Math.max(1, max));
            p.setColor(color);
            c.drawRoundRect(new RectF(x, y, x + width * ratio, y + height), height / 2, height / 2, p);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(sp(11));
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label + " " + value + "/" + max, x + width / 2, y - 6, p);
        }

        private void drawWarrior(Canvas c) {
            drawScreenHeader(c, "محارب سولو", "طور قوتك وحول البوابة إلى ساحة ملكك");
            Stats s = getStats(profile);
            RectF cardR = new RectF(w * 0.06f, h * 0.18f, w * 0.94f, h * 0.50f);
            drawCard(c, cardR, card);
            drawWarriorIcon(c, cardR.left + 70, cardR.top + 90, 54, gold);
            String text = "القوة البدنية: " + profile.strength + "\nالذكاء: " + profile.intelligence + "\nالسرعة: " + profile.speed + "\nالقدرة الدفاعية: " + profile.defense + "\nنقاط غير موزعة: " + profile.points + "\n\nHP: " + s.maxHp + " | هجوم: " + s.attack + " | دفاع: " + s.defense + "\nالسلاح: " + gearName(s.weapon, profile.weaponDur) + "\nالدرع: " + gearName(s.armor, profile.armorDur);
            drawMultiline(c, text, cardR.right - 24, cardR.top + 42, sp(15), Paint.Align.RIGHT, ink, 9);
            float y = h * 0.54f;
            addButton(c, w * 0.08f, y, w * 0.46f, y + h * 0.065f, "+ قوة", ACT_UPGRADE, 0, red);
            addButton(c, w * 0.54f, y, w * 0.92f, y + h * 0.065f, "+ ذكاء", ACT_UPGRADE, 1, purple);
            addButton(c, w * 0.08f, y + h * 0.085f, w * 0.46f, y + h * 0.15f, "+ سرعة", ACT_UPGRADE, 2, cyan);
            addButton(c, w * 0.54f, y + h * 0.085f, w * 0.92f, y + h * 0.15f, "+ دفاع", ACT_UPGRADE, 3, green);
            addButton(c, w * 0.08f, h * 0.88f, w * 0.92f, h * 0.95f, "رجوع", ACT_BACK, 0, Color.rgb(100, 106, 126));
        }

        private void drawShop(Canvas c) {
            drawScreenHeader(c, "متجر البوابة", "عروض عشوائية من عتاد ومستهلكات");
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTextSize(sp(15));
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(Color.WHITE);
            c.drawText("رصيدك: " + profile.coins + " عملة", w * 0.91f, h * 0.155f, p);
            float top = h * 0.19f;
            for (int i = 0; i < offers.length; i++) {
                StoreOffer offer = offers[i];
                float y = top + i * h * 0.096f;
                RectF r = new RectF(w * 0.06f, y, w * 0.94f, y + h * 0.079f);
                drawCard(c, r, card);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(sp(14));
                p.setColor(ink);
                c.drawText((i + 1) + ") " + offer.item.rarity.icon + " " + offer.item.name, r.right - 18, r.top + 27, p);
                p.setTypeface(Typeface.DEFAULT);
                p.setTextSize(sp(12));
                c.drawText(offer.item.slotLabel() + " | " + offer.price + " عملة | " + offer.item.rarity.shortName, r.right - 18, r.top + 55, p);
                addButton(c, r.left + 14, r.top + 14, r.left + 98, r.bottom - 14, "شراء", ACT_BUY, i, gold);
            }
            addButton(c, w * 0.08f, h * 0.88f, w * 0.92f, h * 0.95f, "رجوع", ACT_BACK, 0, Color.rgb(100, 106, 126));
        }

        private void drawBag(Canvas c) {
            drawScreenHeader(c, "حقيبة سولو", "جهز، بيع، واستعد للبوابة القادمة");
            List<Item> inv = inventoryList();
            if (inv.isEmpty()) {
                drawCenterText(c, "حقيبتك فارغة. افتح المتجر أو اهزم وحوشاً للحصول على غنائم.", h * 0.42f, sp(18), Color.WHITE);
            } else {
                float top = h * 0.17f;
                int max = Math.min(inv.size(), 7);
                for (int i = 0; i < max; i++) {
                    Item item = inv.get(i);
                    int count = profile.inventory.getOrDefault(item.id, 0);
                    float y = top + i * h * 0.091f;
                    RectF r = new RectF(w * 0.06f, y, w * 0.94f, y + h * 0.075f);
                    drawCard(c, r, card);
                    p.setTextAlign(Paint.Align.RIGHT);
                    p.setTypeface(Typeface.DEFAULT_BOLD);
                    p.setTextSize(sp(13));
                    p.setColor(ink);
                    String equipped = item.id.equals(profile.weaponId) || item.id.equals(profile.armorId) ? " مجهز" : "";
                    c.drawText(item.rarity.icon + " " + item.name + " ×" + count + equipped, r.right - 18, r.top + 28, p);
                    p.setTypeface(Typeface.DEFAULT);
                    p.setTextSize(sp(11));
                    c.drawText(item.slotLabel() + " | قيمة البيع " + (item.price * 55 / 100), r.right - 18, r.top + 54, p);
                    addButton(c, r.left + 14, r.top + 10, r.left + 90, r.bottom - 10, "بيع", ACT_SELL, i, red);
                    addButton(c, r.left + 100, r.top + 10, r.left + 184, r.bottom - 10, "تجهيز", ACT_EQUIP, i, green);
                }
            }
            addButton(c, w * 0.08f, h * 0.88f, w * 0.92f, h * 0.95f, "رجوع", ACT_BACK, 0, Color.rgb(100, 106, 126));
        }

        private void drawMissions(Canvas c) {
            drawScreenHeader(c, "مهام سولو", "المهام تمنح نقاط التطوير مثل نظام البوت الأصلي");
            Mission m = MISSION_LIST[Math.min(profile.missionIndex, MISSION_LIST.length - 1)];
            RectF r = new RectF(w * 0.06f, h * 0.19f, w * 0.94f, h * 0.62f);
            drawCard(c, r, card);
            StringBuilder sb = new StringBuilder();
            sb.append("المهمة رقم: ").append(profile.missionIndex + 1).append(" / ").append(MISSION_LIST.length).append("\n");
            sb.append(m.title).append("\n").append(m.desc).append("\nالجائزة: ").append(m.points).append(" نقاط تطوير\n\nالتقدم:");
            for (Req req : m.reqs) sb.append("\n• ").append(labelForReq(req.key)).append(": ").append(Math.min(req.need, getReqValue(req.key))).append("/").append(req.need);
            drawMultiline(c, sb.toString(), r.right - 26, r.top + 42, sp(16), Paint.Align.RIGHT, ink, 12);
            addButton(c, w * 0.08f, h * 0.88f, w * 0.92f, h * 0.95f, "رجوع", ACT_BACK, 0, Color.rgb(100, 106, 126));
        }

        private void drawHistory(Canvas c) {
            drawScreenHeader(c, "تاريخي", "سجل الدم والبوابات المفتوحة");
            RectF r = new RectF(w * 0.06f, h * 0.18f, w * 0.94f, h * 0.67f);
            drawCard(c, r, card);
            String text = "إجمالي القتلات: " + profile.totalKills + "\nالانتصارات: " + profile.totalWins + "\nالخسائر: " + profile.totalLosses + "\nأفضل سلسلة: " + profile.bestStreak + "\nأعلى ضربة: " + profile.highestDamage + "\n\nبيتا: " + profile.killsByRarity[0] + "\nألفا: " + profile.killsByRarity[1] + "\nنخبة: " + profile.killsByRarity[2] + "\nأسطوري: " + profile.killsByRarity[3];
            drawMultiline(c, text, r.right - 28, r.top + 42, sp(17), Paint.Align.RIGHT, ink, 12);
            addButton(c, w * 0.08f, h * 0.88f, w * 0.92f, h * 0.95f, "رجوع", ACT_BACK, 0, Color.rgb(100, 106, 126));
        }

        private void drawResult(Canvas c) {
            drawTitle(c, resultTitle, "البوابة انتهت، والقرار القادم بيدك");
            RectF r = new RectF(w * 0.08f, h * 0.28f, w * 0.92f, h * 0.62f);
            drawCard(c, r, card);
            drawMultiline(c, resultBody, r.right - 26, r.top + 48, sp(17), Paint.Align.RIGHT, ink, 10);
            addButton(c, w * 0.10f, h * 0.69f, w * 0.90f, h * 0.76f, "بوابة جديدة", ACT_START_BATTLE, 0, purple);
            addButton(c, w * 0.10f, h * 0.79f, w * 0.90f, h * 0.86f, "رجوع للقائمة", ACT_BACK, 0, Color.rgb(100, 106, 126));
        }

        private void drawScreenHeader(Canvas c, String title, String subtitle) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            p.setTextSize(sp(30));
            p.setColor(Color.WHITE);
            c.drawText(title, w / 2f, h * 0.075f, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(sp(13));
            p.setColor(Color.argb(220, 255, 255, 255));
            c.drawText(subtitle, w / 2f, h * 0.112f, p);
        }

        private void drawTitle(Canvas c, String title, String subtitle) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            p.setTextSize(sp(38));
            p.setShader(new LinearGradient(0, h * 0.07f, w, h * 0.13f, gold, cyan, Shader.TileMode.CLAMP));
            c.drawText(title, w / 2f, h * 0.12f, p);
            p.setShader(null);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(sp(14));
            p.setColor(Color.argb(225, 255, 255, 255));
            c.drawText(subtitle, w / 2f, h * 0.165f, p);
        }

        private void drawCard(Canvas c, RectF r, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(65, 0, 0, 0));
            c.drawRoundRect(new RectF(r.left, r.top + 8, r.right, r.bottom + 8), 32, 32, p);
            p.setColor(color);
            c.drawRoundRect(r, 32, 32, p);
        }

        private void addButton(Canvas c, float left, float top, float right, float bottom, String label, int action, int index, int color) {
            RectF r = new RectF(left, top, right, bottom);
            buttons.add(new Button(r, label, action, index));
            p.setShader(new LinearGradient(left, top, right, bottom, lighten(color), color, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 28, 28, p);
            p.setShader(null);
            p.setColor(Color.argb(70, 0, 0, 0));
            c.drawRoundRect(new RectF(left, bottom - 8, right, bottom), 28, 28, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(sp(15));
            p.setColor(Color.WHITE);
            Paint.FontMetrics fm = p.getFontMetrics();
            float y = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f;
            c.drawText(label, (left + right) / 2f, y, p);
        }

        private void drawMonster(Canvas c, float x, float y, float r, Boss b) {
            float breathing = (float) Math.sin(pulse * 4f) * r * 0.05f;
            r += breathing;
            int color = b.rarity.color;
            p.setShader(new RadialGradient(x - r * 0.35f, y - r * 0.45f, r * 1.5f, lighten(color), color, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
            p.setShader(null);
            p.setColor(Color.argb(110, 0, 0, 0));
            c.drawOval(x - r * 0.95f, y + r * 0.70f, x + r * 0.95f, y + r * 0.98f, p);
            p.setColor(Color.rgb(25, 25, 32));
            c.drawCircle(x - r * 0.35f, y - r * 0.15f, r * 0.10f, p);
            c.drawCircle(x + r * 0.35f, y - r * 0.15f, r * 0.10f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(4f, r * 0.08f));
            p.setColor(Color.argb(210, 255, 255, 255));
            c.drawArc(x - r * 0.42f, y + r * 0.10f, x + r * 0.42f, y + r * 0.55f, 15, 150, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(160, 255, 255, 255));
            for (int i = 0; i < 6; i++) {
                float a = (float) (pulse * 0.7f + i * Math.PI / 3);
                c.drawCircle(x + (float) Math.cos(a) * r * 1.35f, y + (float) Math.sin(a) * r * 1.35f, r * 0.055f, p);
            }
        }

        private void drawWarriorIcon(Canvas c, float x, float y, float r, int color) {
            p.setColor(Color.argb(100, 0, 0, 0));
            c.drawOval(x - r * 0.9f, y + r * 0.72f, x + r * 0.9f, y + r * 1.0f, p);
            p.setShader(new RadialGradient(x - r * 0.35f, y - r * 0.35f, r * 1.3f, Color.WHITE, color, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
            p.setShader(null);
            p.setColor(Color.rgb(40, 38, 52));
            c.drawCircle(x - r * .26f, y - r * .1f, r * .08f, p);
            c.drawCircle(x + r * .26f, y - r * .1f, r * .08f, p);
            Path sword = new Path();
            sword.moveTo(x + r * 0.55f, y - r * 0.75f);
            sword.lineTo(x + r * 1.30f, y - r * 1.55f);
            sword.lineTo(x + r * 1.42f, y - r * 1.42f);
            sword.lineTo(x + r * 0.68f, y - r * 0.62f);
            sword.close();
            p.setColor(Color.rgb(230, 236, 255));
            c.drawPath(sword, p);
        }

        private void drawMultiline(Canvas c, String text, float x, float y, float size, Paint.Align align, int color, int maxLines) {
            p.setTextAlign(align);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            p.setTextSize(size);
            p.setColor(color);
            String[] lines = String.valueOf(text).split("\\n");
            float lineH = size * 1.45f;
            int count = Math.min(maxLines, lines.length);
            for (int i = 0; i < count; i++) c.drawText(lines[i], x, y + i * lineH, p);
        }

        private void drawCenterText(Canvas c, String text, float y, float size, int color) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(size);
            p.setColor(color);
            c.drawText(text, w / 2f, y, p);
        }

        private void drawToast(Canvas c) {
            if (toastTime <= 0 || toast == null || toast.isEmpty()) return;
            p.setTextSize(sp(14));
            p.setTypeface(Typeface.DEFAULT_BOLD);
            float tw = p.measureText(toast) + 52;
            RectF r = new RectF((w - tw) / 2f, h * 0.085f, (w + tw) / 2f, h * 0.14f);
            p.setColor(Color.argb(225, 20, 23, 35));
            c.drawRoundRect(r, 24, 24, p);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(toast, w / 2f, (r.top + r.bottom) / 2f - (fm.ascent + fm.descent) / 2f, p);
        }

        private void toast(String msg) {
            toast = msg;
            toastTime = 2.1f;
        }

        private void burst(float x, float y, int color, int count) {
            for (int i = 0; i < count; i++) {
                float a = random.nextFloat() * (float) Math.PI * 2f;
                float v = 130f + random.nextFloat() * 520f;
                particles.add(new Particle(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, 0.35f + random.nextFloat() * 0.65f, color));
            }
        }

        private void updateParticles(float dt) {
            for (int i = particles.size() - 1; i >= 0; i--) {
                Particle part = particles.get(i);
                part.life -= dt;
                part.x += part.vx * dt;
                part.y += part.vy * dt;
                part.vy += 420f * dt;
                if (part.life <= 0) particles.remove(i);
            }
        }

        private void drawParticles(Canvas c) {
            for (Particle part : particles) {
                int alpha = (int) (255 * clamp01(part.life));
                p.setColor((alpha << 24) | (part.color & 0x00FFFFFF));
                c.drawCircle(part.x, part.y, 4 + 8 * clamp01(part.life), p);
            }
        }

        private void ping(long ms) {
            try {
                if (vibrator == null) return;
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                else vibrator.vibrate(ms);
            } catch (Throwable ignored) {}
        }

        private String gearName(Item item, int dur) {
            if (item == null) return "بدون";
            return item.name + " (" + Math.max(0, dur) + "/" + item.durability + ")";
        }

        private String labelForReq(String key) {
            if (key.equals("total")) return "الإجمالي";
            if (key.equals("fastWins")) return "فوز سريع";
            if (key.equals("armored")) return "مدرع";
            if (key.equals("cunning")) return "دهاء/سحر";
            if (key.equals("swift")) return "سريع";
            if (key.equals("poison")) return "سام";
            if (key.equals("magic")) return "سحري";
            if (key.equals("fear")) return "مرعب";
            int idx = rarityIndex(key);
            return idx >= 0 ? RARITIES[idx].shortName : key;
        }

        private int sp(float value) { return Math.max(10, (int) (value * getResources().getDisplayMetrics().scaledDensity)); }
        private int rand(int min, int max) { return min + random.nextInt(Math.max(1, max - min + 1)); }
        private int clampInt(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
        private float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
        private int lighten(int color) {
            int r = Math.min(255, Color.red(color) + 45);
            int g = Math.min(255, Color.green(color) + 45);
            int b = Math.min(255, Color.blue(color) + 45);
            return Color.rgb(r, g, b);
        }

        private Item itemById(String id) {
            if (id == null) return null;
            for (Item item : CATALOG) if (item.id.equals(id)) return item;
            return null;
        }

        private int rarityIndex(String key) {
            for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].key.equals(key)) return i;
            return -1;
        }

        private int rarityRank(String key) { return Math.max(0, rarityIndex(key)); }

        static class Button {
            RectF rect;
            String label;
            int action;
            int index;
            Button(RectF rect, String label, int action, int index) { this.rect = rect; this.label = label; this.action = action; this.index = index; }
        }

        static class Particle {
            float x, y, vx, vy, life;
            int color;
            Particle(float x, float y, float vx, float vy, float life, int color) { this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.color = color; }
        }

        static class Profile {
            int strength = 1, intelligence = 1, speed = 1, defense = 1, points = 0, coins = 150;
            int totalKills = 0, totalWins = 0, totalLosses = 0, fastWins = 0, streak = 0, bestStreak = 0, highestDamage = 0;
            int armoredKills = 0, cunningKills = 0, swiftKills = 0, poisonKills = 0, magicKills = 0, fearKills = 0;
            int missionIndex = 0;
            int[] killsByRarity = new int[]{0, 0, 0, 0};
            String weaponId = null, armorId = null;
            int weaponDur = 0, armorDur = 0;
            HashMap<String, Integer> inventory = new HashMap<>();

            static Profile load(SharedPreferences sp) {
                Profile pr = new Profile();
                pr.strength = sp.getInt("strength", 1);
                pr.intelligence = sp.getInt("intelligence", 1);
                pr.speed = sp.getInt("speed", 1);
                pr.defense = sp.getInt("defense", 1);
                pr.points = sp.getInt("points", 0);
                pr.coins = sp.getInt("coins", 150);
                pr.totalKills = sp.getInt("totalKills", 0);
                pr.totalWins = sp.getInt("totalWins", 0);
                pr.totalLosses = sp.getInt("totalLosses", 0);
                pr.fastWins = sp.getInt("fastWins", 0);
                pr.streak = sp.getInt("streak", 0);
                pr.bestStreak = sp.getInt("bestStreak", 0);
                pr.highestDamage = sp.getInt("highestDamage", 0);
                pr.armoredKills = sp.getInt("armoredKills", 0);
                pr.cunningKills = sp.getInt("cunningKills", 0);
                pr.swiftKills = sp.getInt("swiftKills", 0);
                pr.poisonKills = sp.getInt("poisonKills", 0);
                pr.magicKills = sp.getInt("magicKills", 0);
                pr.fearKills = sp.getInt("fearKills", 0);
                pr.missionIndex = sp.getInt("missionIndex", 0);
                pr.killsByRarity = new int[]{sp.getInt("kill_beta", 0), sp.getInt("kill_alpha", 0), sp.getInt("kill_elite", 0), sp.getInt("kill_legendary", 0)};
                pr.weaponId = emptyToNull(sp.getString("weaponId", ""));
                pr.armorId = emptyToNull(sp.getString("armorId", ""));
                pr.weaponDur = sp.getInt("weaponDur", 0);
                pr.armorDur = sp.getInt("armorDur", 0);
                String inv = sp.getString("inventory", "");
                if (inv != null && !inv.isEmpty()) {
                    String[] rows = inv.split(",");
                    for (String row : rows) {
                        String[] parts = row.split(":");
                        if (parts.length == 2) {
                            try { int count = Integer.parseInt(parts[1]); if (count > 0) pr.inventory.put(parts[0], count); } catch (Exception ignored) {}
                        }
                    }
                }
                return pr;
            }

            void save(SharedPreferences sp) {
                StringBuilder inv = new StringBuilder();
                for (Map.Entry<String, Integer> e : inventory.entrySet()) {
                    if (e.getValue() == null || e.getValue() <= 0) continue;
                    if (inv.length() > 0) inv.append(',');
                    inv.append(e.getKey()).append(':').append(e.getValue());
                }
                sp.edit()
                        .putInt("strength", strength).putInt("intelligence", intelligence).putInt("speed", speed).putInt("defense", defense)
                        .putInt("points", points).putInt("coins", coins).putInt("totalKills", totalKills).putInt("totalWins", totalWins)
                        .putInt("totalLosses", totalLosses).putInt("fastWins", fastWins).putInt("streak", streak).putInt("bestStreak", bestStreak)
                        .putInt("highestDamage", highestDamage).putInt("armoredKills", armoredKills).putInt("cunningKills", cunningKills)
                        .putInt("swiftKills", swiftKills).putInt("poisonKills", poisonKills).putInt("magicKills", magicKills).putInt("fearKills", fearKills)
                        .putInt("missionIndex", missionIndex).putInt("kill_beta", killsByRarity[0]).putInt("kill_alpha", killsByRarity[1])
                        .putInt("kill_elite", killsByRarity[2]).putInt("kill_legendary", killsByRarity[3])
                        .putString("weaponId", weaponId == null ? "" : weaponId).putString("armorId", armorId == null ? "" : armorId)
                        .putInt("weaponDur", weaponDur).putInt("armorDur", armorDur).putString("inventory", inv.toString()).apply();
            }

            static String emptyToNull(String s) { return s == null || s.isEmpty() ? null : s; }
        }

        static class Stats {
            int maxHp, attack, intelligence, speed, defense, magicShield, poison, pierce, strength;
            Item weapon, armor;
        }

        static class Battle {
            Boss boss;
            int playerHp, playerMaxHp, round, maxRounds;
            boolean finished;
            String lastLog = "";
        }

        static class Boss {
            String name;
            Rarity rarity;
            Ability ability;
            int hp, maxHp, armor, maxArmor, offense, cunning, speed, defense;
        }

        static class StoreOffer {
            Item item;
            int price;
            StoreOffer(Item item, int price) { this.item = item; this.price = price; }
        }

        static class Rarity {
            String key, name, shortName, icon;
            int color, baseHp, baseArmor, rewardMin, rewardMax;
            float power;
            Rarity(String key, String name, String shortName, String icon, int color, float power, int baseHp, int baseArmor, int rewardMin, int rewardMax) {
                this.key = key; this.name = name; this.shortName = shortName; this.icon = icon; this.color = color; this.power = power; this.baseHp = baseHp; this.baseArmor = baseArmor; this.rewardMin = rewardMin; this.rewardMax = rewardMax;
            }
        }

        static class Ability {
            String id, name, desc, minRarity;
            ArrayList<String> tags = new ArrayList<>();
            Ability(String id, String name, String desc, String minRarity, String... tags) {
                this.id = id; this.name = name; this.desc = desc; this.minRarity = minRarity;
                for (String t : tags) this.tags.add(t);
            }
        }

        static class Item {
            String id, name, slot, desc;
            Rarity rarity;
            int price, attack, speed, intelligence, defense, poison, pierce, durability, magicShield, hp;
            Item(String id, String name, String slot, Rarity rarity, int price, int attack, int speed, int intelligence, int defense, int poison, int pierce, int durability, int magicShield, int hp, String desc) {
                this.id = id; this.name = name; this.slot = slot; this.rarity = rarity; this.price = price; this.attack = attack; this.speed = speed; this.intelligence = intelligence; this.defense = defense; this.poison = poison; this.pierce = pierce; this.durability = durability; this.magicShield = magicShield; this.hp = hp; this.desc = desc;
            }
            String slotLabel() { return slot.equals("weapon") ? "سلاح" : slot.equals("armor") ? "درع" : "مستهلك"; }
        }

        static class Req {
            String key; int need;
            Req(String key, int need) { this.key = key; this.need = need; }
        }

        static class Mission {
            String title, desc; int points; Req[] reqs;
            Mission(String title, String desc, int points, Req... reqs) { this.title = title; this.desc = desc; this.points = points; this.reqs = reqs; }
        }

        static final Rarity[] RARITIES = new Rarity[]{
                new Rarity("beta", "وحش بيتا", "بيتا", "🟢", Color.rgb(74, 210, 122), 1.00f, 25, 4, 18, 38),
                new Rarity("alpha", "وحش ألفا", "ألفا", "🔵", Color.rgb(84, 156, 255), 1.45f, 65, 14, 42, 84),
                new Rarity("elite", "وحش نخبة", "نخبة", "🟣", Color.rgb(163, 98, 255), 2.10f, 120, 30, 95, 170),
                new Rarity("legendary", "وحش أسطوري", "أسطوري", "🟠", Color.rgb(255, 151, 61), 3.10f, 210, 56, 220, 420)
        };

        static final Ability[] ABILITIES = new Ability[]{
                new Ability("brutal_charge", "اندفاع ساحق", "هجوم جسدي عنيف، الدفاع العالي يقلل خطره.", "beta", "physical"),
                new Ability("cunning_mind", "دهاء مفترس", "يقرأ حركة المحارب، والذكاء العالي يكسر خداعه.", "beta", "cunning"),
                new Ability("shadow_step", "خطوة ظل", "يتفادى جزءاً من الضرر، والسرعة العالية تكشف حركته.", "beta", "speed"),
                new Ability("stone_skin", "جلد حجري", "درعه صلب جداً، القوة البدنية تكسر طبقاته.", "beta", "armor"),
                new Ability("venom_bite", "عضة سمية", "يترك سماً مؤلماً بعد الهجوم.", "alpha", "poison"),
                new Ability("war_roar", "زئير الحرب", "يربك الجسد ويخفض قوة الضربة إن كان الدفاع ضعيفاً.", "alpha", "fear"),
                new Ability("mana_shell", "قوقعة مانا", "حاجز سحري يمتص الضرر.", "alpha", "magic"),
                new Ability("berserk", "جنون متصاعد", "يزداد خطره مع كل جولة.", "elite", "scaling"),
                new Ability("royal_command", "أمر ملكي", "يرفع درعه وهجومه إذا شعر أن المحارب متردد.", "elite", "elite"),
                new Ability("ancient_curse", "لعنة قديمة", "تخترق الدفاع العادي والدرع السحري يخفف أثرها.", "legendary", "magic", "curse"),
                new Ability("titan_guard", "حراسة التيتان", "كتلة دفاعية ضخمة؛ القوة والذكاء معاً أفضل حل.", "legendary", "legendary", "armor"),
                new Ability("void_hunger", "جوع الفراغ", "يمتص جزءاً من طاقة المحارب إن طال القتال.", "legendary", "legendary", "drain")
        };

        static final String[][] MONSTER_NAMES = new String[][]{
                {"غول صغير", "أورك متشرد", "ذئب كهف", "عقرب رملي", "حارس طين", "خفاش دموي", "سحلية صدئة", "غريم المستنقع", "هيكل ضائع", "حارس فحم"},
                {"غولم حجري", "قائد الأورك", "صياد الظلال", "عنكبوت الكهوف", "ثعبان العقيق", "نمر البرق", "حارس البوابة", "مارد المرآة", "حاصد المستنقع"},
                {"ملك النمل", "فارس الرماد", "جزار القلعة", "وحش الياقوت", "كاهن العظام", "غولم البركان", "حارس العرش المكسور", "سيد العقارب"},
                {"تنين البوابة السوداء", "ملك النمل القديم", "غولم الزمن", "أورك التاج الأحمر", "حوت الفراغ", "شيطان المرآة الكبرى", "التيتان النائم", "ملك الهاوية"}
        };

        static final Item[] CATALOG = new Item[]{
                new Item("rusty_sword", "سيف صدئ", "weapon", RARITIES[0], 90, 10, 1, 0, 0, 0, 1, 45, 0, 0, "سيف بداية بسيط لكنه أفضل من قبضة اليد."),
                new Item("hunter_dagger", "خنجر الصياد", "weapon", RARITIES[0], 120, 8, 5, 0, 0, 0, 0, 42, 0, 0, "خفيف وسريع ضد الوحوش المراوغة."),
                new Item("oak_spear", "رمح البلوط", "weapon", RARITIES[0], 145, 12, 2, 0, 0, 0, 2, 50, 0, 0, "مداه جيد ويكسر جزءاً بسيطاً من الدرع."),
                new Item("blue_blade", "النصل الأزرق", "weapon", RARITIES[1], 260, 18, 4, 2, 0, 0, 4, 65, 0, 0, "نصل متوازن يناسب مقاتلاً ذكياً."),
                new Item("scorpion_spear", "رمح العقرب", "weapon", RARITIES[1], 330, 16, 3, 0, 0, 5, 3, 60, 0, 0, "يترك سماً يلتهم صحة الوحش تدريجياً."),
                new Item("giant_hammer", "مطرقة العملاق", "weapon", RARITIES[1], 360, 25, -2, 0, 1, 0, 6, 76, 0, 0, "بطيئة لكنها ممتازة ضد الدروع."),
                new Item("venom_edge", "نصل السم", "weapon", RARITIES[2], 620, 26, 5, 2, 0, 9, 5, 84, 0, 0, "سلاح نخبة يراكم الضرر."),
                new Item("thunder_maul", "مطرقة الرعد", "weapon", RARITIES[2], 760, 38, -1, 1, 2, 0, 10, 92, 0, 0, "تسحق الدروع الثقيلة."),
                new Item("ant_king_blade", "شفرة ملك النمل", "weapon", RARITIES[3], 1450, 48, 7, 4, 0, 5, 12, 120, 0, 0, "شفرة أسطورية للبوابات العميقة."),
                new Item("void_saber", "سيف الفراغ", "weapon", RARITIES[3], 1750, 55, 4, 8, 0, 0, 15, 130, 0, 0, "يضرب الجسد والدرع السحري معاً."),
                new Item("leather_armor", "درع جلد متين", "armor", RARITIES[0], 105, 0, 1, 0, 9, 0, 0, 55, 0, 12, "خفيف ومناسب لأول البوابات."),
                new Item("stone_shield", "ترس صخري", "armor", RARITIES[0], 150, 0, -1, 0, 15, 0, 0, 70, 0, 22, "دفاع جيد ضد الهجمات الجسدية."),
                new Item("mage_cloak", "عباءة الساحر", "armor", RARITIES[1], 290, 0, 2, 6, 8, 0, 0, 62, 8, 10, "مفيدة ضد الدهاء واللعنات."),
                new Item("iron_guard", "درع الحارس الحديدي", "armor", RARITIES[1], 360, 0, -2, 0, 24, 0, 0, 88, 2, 35, "يحمي من الوحوش ذات الهجوم العالي."),
                new Item("ant_king_shell", "درع ملك النمل", "armor", RARITIES[2], 780, 2, 0, 3, 34, 0, 0, 105, 8, 45, "درع نخبة يوازن بين الدفاع والذكاء."),
                new Item("celestial_aegis", "درع سماوي", "armor", RARITIES[3], 1650, 0, 1, 8, 48, 0, 0, 140, 22, 70, "حماية أسطورية ضد اللعنات."),
                new Item("sharp_oil", "زيت شحذ", "consumable", RARITIES[0], 65, 0, 0, 0, 0, 0, 0, 0, 0, 0, "نسخة لاحقة: يزيد الهجوم مؤقتاً."),
                new Item("focus_stone", "حجر تركيز", "consumable", RARITIES[1], 150, 0, 0, 0, 0, 0, 0, 0, 0, 0, "نسخة لاحقة: يرفع الذكاء مؤقتاً."),
                new Item("quick_boots", "غبار السرعة", "consumable", RARITIES[0], 70, 0, 0, 0, 0, 0, 0, 0, 0, 0, "نسخة لاحقة: يرفع السرعة مؤقتاً.")
        };

        static final Mission[] MISSION_LIST = new Mission[]{
                new Mission("بداية المحارب", "اقتل 3 وحوش بيتا.", 5, new Req("beta", 3)),
                new Mission("اختبار البوابة", "اقتل 5 وحوش من أي ندرة.", 4, new Req("total", 5)),
                new Mission("كاسر الغيلان", "اقتل 8 وحوش بيتا.", 6, new Req("beta", 8)),
                new Mission("خطوة نحو ألفا", "اقتل 3 وحوش ألفا.", 7, new Req("alpha", 3)),
                new Mission("لا تهاب الدرع", "اهزم 6 وحوش تمتلك درعاً عالياً.", 7, new Req("armored", 6)),
                new Mission("عين ذكية", "اهزم 5 وحوش ذات دهاء أو سحر.", 8, new Req("cunning", 5)),
                new Mission("صياد سريع", "اهزم 5 وحوش مراوغة أو سريعة.", 8, new Req("swift", 5)),
                new Mission("أول نخبة", "اقتل وحش نخبة واحد.", 10, new Req("elite", 1)),
                new Mission("المحارب الثابت", "اقتل 15 وحشاً من أي ندرة.", 8, new Req("total", 15)),
                new Mission("حاصد الألفا", "اقتل 8 وحوش ألفا.", 10, new Req("alpha", 8)),
                new Mission("ضد السم", "اهزم 4 وحوش سامة.", 9, new Req("poison", 4)),
                new Mission("ثلاثة نخبة", "اقتل 3 وحوش نخبة.", 12, new Req("elite", 3)),
                new Mission("سيد الجولات", "اربح 10 معارك قبل الجولة الثالثة.", 11, new Req("fastWins", 10)),
                new Mission("مقاوم اللعنات", "اهزم 4 وحوش سحرية أو ملعونة.", 12, new Req("magic", 4)),
                new Mission("بوابة عميقة", "اقتل 30 وحشاً من أي ندرة.", 12, new Req("total", 30)),
                new Mission("أسطورة يوتا", "اقتل 5 وحوش أسطورية.", 35, new Req("legendary", 5)),
                new Mission("محارب لا ينتهي", "اقتل 250 وحشاً من أي ندرة.", 60, new Req("total", 250))
        };
    }
}
