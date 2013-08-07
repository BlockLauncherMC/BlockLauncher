package net.zhuoweizhang.mcpelauncher;

import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.mozilla.javascript.*;
import org.mozilla.javascript.annotations.JSFunction;

import com.mojang.minecraftpe.MainActivity;

import static net.zhuoweizhang.mcpelauncher.PatchManager.join;
import static net.zhuoweizhang.mcpelauncher.PatchManager.blankArray;

public class ScriptManager {

	public static final String SCRIPTS_DIR = "modscripts";

	public static List<ScriptState> scripts = new ArrayList<ScriptState>();

	public static android.content.Context androidContext;

	public static Set<String> enabledScripts = new HashSet<String>();

	/** Is the currently loaded world a multiplayer world? */
	public static boolean isRemote = false;

	private static final int AXIS_X = 0;
	private static final int AXIS_Y = 1;
	private static final int AXIS_Z = 2;

	public static void loadScript(Reader in, String sourceName) throws IOException {
		if (isRemote) throw new RuntimeException("Not available in multiplayer");
		Context ctx = Context.enter();
		Script script = ctx.compileReader(in, sourceName, 0, null);
		initJustLoadedScript(ctx, script, sourceName);
		Context.exit();
	}

	public static void loadScript(File file) throws IOException {
		Reader in = null;
		try {
			in = new FileReader(file);
			loadScript(in, file.getName());
		} finally {
			if (in != null) in.close();
		}
	}

	public static void initJustLoadedScript(Context ctx, Script script, String sourceName) {
		Scriptable scope = ctx.initStandardObjects(new BlockHostObject(), false);
		String[] names = getAllJsFunctions(BlockHostObject.class);
		((ScriptableObject) scope).defineFunctionProperties(names, BlockHostObject.class, ScriptableObject.DONTENUM);

		ScriptState state = new ScriptState(script, scope, sourceName);
		script.exec(ctx, scope);
		scripts.add(state);
	}

	public static void callScriptMethod(String functionName, Object... args) {
		if (isRemote) return; //No script loading/callbacks when in a remote world
		Context ctx = Context.enter();
		for (ScriptState state: scripts) {
			Scriptable scope = state.scope;
			Object obj = scope.get(functionName, scope);
			if (obj != null && obj instanceof Function) {
				try {
					((Function) obj).call(ctx, scope, scope, args);
				} catch (Exception e) {
					e.printStackTrace();
					reportScriptError(state, e);
				}
			}
		}
	}

	public static void useItemOnCallback(int x, int y, int z, int itemid, int blockid, int side) {
		callScriptMethod("useItem", x, y, z, itemid, blockid, side);
	}

	public static void setLevelCallback(boolean hasLevel, boolean isRemote) {
		System.out.println("Level: " + hasLevel);
		ScriptManager.isRemote = isRemote;
		callScriptMethod("newLevel", hasLevel);
		if (MainActivity.currentMainActivity != null) {
			MainActivity main = MainActivity.currentMainActivity.get();
			if (main != null) {
				main.setLevelCallback(isRemote);
			}
		}
	}

	public static void leaveGameCallback(boolean thatboolean) {
		ScriptManager.isRemote = false;
		callScriptMethod("leaveGame");
		if (MainActivity.currentMainActivity != null) {
			MainActivity main = MainActivity.currentMainActivity.get();
			if (main != null) {
				main.leaveGameCallback();
			}
		}
	}

	public static void attackCallback(long attacker, long victim) {
		callScriptMethod("attackHook", new NativePointer(attacker), new NativePointer(victim));
	}

	public static void tickCallback() {
		callScriptMethod("modTick");
	}

	public static void init(android.content.Context cxt) throws IOException {
		//set up hooks
		int versionCode = 0;
		try {
			versionCode = cxt.getPackageManager().getPackageInfo("com.mojang.minecraftpe", 0).versionCode;
		} catch (PackageManager.NameNotFoundException e) {
			//impossible, as if the package isn't installed, the app would've quit before loading scripts
		}
		nativeSetupHooks(versionCode);
		scripts.clear();
		androidContext = cxt.getApplicationContext();
		loadEnabledScripts();
	}

	public static void removeScript(String scriptId) {
		for (int i = scripts.size() - 1; i >= 0; i--) {
			if (scripts.get(i).name.equals(scriptId)) {
				scripts.remove(i);
				break;
			}
		}
	}

	public static void reloadScript(File file) throws IOException {
		removeScript(file.getName());
		loadScript(file);
	}

	private static void reportScriptError(ScriptState state, Throwable t) {
		if (MainActivity.currentMainActivity != null) {
			MainActivity main = MainActivity.currentMainActivity.get();
			if (main != null) {
				main.scriptErrorCallback(state.name, t);
			}
		}
	}


	//following taken from the patch manager
	public static Set<String> getEnabledScripts() {
		return enabledScripts;
	}

	private static void setEnabled(String name, boolean state) throws IOException {
		if (state) {
			reloadScript(getScriptFile(name));
			enabledScripts.add(name);
		} else {
			enabledScripts.remove(name);
			removeScript(name);
		}
		saveEnabledScripts();
	}

	public static void setEnabled(File[] files, boolean state) throws IOException {
		for (File file: files) {
			String name = file.getAbsolutePath();
			if (name == null || name.length() <= 0) continue;
			if (state) {
				reloadScript(getScriptFile(name));
				enabledScripts.add(name);
			} else {
				enabledScripts.remove(name);
				removeScript(name);
			}
		}
		saveEnabledScripts();
	}

	public static void setEnabled(File file, boolean state) throws IOException {
		setEnabled(file.getName(), state);
	}

	private static boolean isEnabled(String name) {
		return enabledScripts.contains(name);
	}

	public static boolean isEnabled(File file) {
		return isEnabled(file.getName());
	}

	public static void removeDeadEntries(Collection<String> allPossibleFiles) {
		enabledScripts.retainAll(allPossibleFiles);
		saveEnabledScripts();
	}

	protected static void loadEnabledScripts() throws IOException {
		SharedPreferences sharedPrefs = androidContext.getSharedPreferences(MainMenuOptionsActivity.PREFERENCES_NAME, 0);
		String enabledScriptsStr = sharedPrefs.getString("enabledScripts", "");
		enabledScripts = new HashSet<String>(Arrays.asList(enabledScriptsStr.split(";")));
		for (String name: enabledScripts) {
			//load all scripts into the script interpreter
			loadScript(getScriptFile(name));
		}
	}

	protected static void saveEnabledScripts() {
		SharedPreferences sharedPrefs = androidContext.getSharedPreferences(MainMenuOptionsActivity.PREFERENCES_NAME, 0);
		SharedPreferences.Editor edit = sharedPrefs.edit();
		edit.putString("enabledScripts", join(enabledScripts.toArray(blankArray), ";"));
		edit.putInt("scriptManagerVersion", 1);
		edit.apply();
	}

	public static File getScriptFile(String scriptId) {
		File scriptsFolder = androidContext.getDir(SCRIPTS_DIR, 0);
		return new File(scriptsFolder, scriptId);
	}
	//end script manager controls

	private static String[] getAllJsFunctions(Class clazz) {
		List<String> allList = new ArrayList<String>();
		for (Method met: clazz.getMethods()) {
			if (met.getAnnotation(JSFunction.class) != null) {
				allList.add(met.getName());
			}
		}
		return allList.toArray(blankArray);
	}

	public static native float nativeGetPlayerLoc(int axis);
	public static native long nativeGetPlayerEnt();
	public static native long nativeGetLevel();
	public static native void nativeSetPosition(long entity, float x, float y, float z);
	public static native void nativeSetVel(long ent, float vel, int axis);
	public static native void nativeExplode(float x, float y, float z, float radius);
	public static native void nativeAddItemInventory(int id, int amount);
	public static native void nativeRideAnimal(long rider, long mount);
	public static native int nativeGetCarriedItem();
	public static native void nativePreventDefault();
	public static native void nativeSetTile(int x, int y, int z, int id, int damage);
	public static native void nativeSpawnEntity(float x, float y, float z, int entityType);

	//0.2
	public static native void nativeSetNightMode(boolean isNight);
	public static native int nativeGetTile(int x, int y, int z);
	public static native void nativeSetPositionRelative(long entity, float x, float y, float z);
	public static native void nativeSetRot(long ent, float yaw, float pitch);
	//0.3
	public static native float nativeGetYaw(long ent);
	public static native float nativeGetPitch(long ent);

	public static native void nativeSetupHooks(int versionCode);

	public static class ScriptState {
		public Script script;
		public Scriptable scope;
		public String name;
		protected ScriptState(Script script, Scriptable scope, String name) {
			this.script = script;
			this.scope = scope;
			this.name = name;
		}
	}

	private static class BlockHostObject extends ScriptableObject {
		private NativePointer playerEnt = new NativePointer(0);
		@Override
		public String getClassName() {
			return "BlockHostObject";
		}
		@JSFunction
		public void print(String str) {
			System.out.println(str);
			if (MainActivity.currentMainActivity != null) {
				MainActivity main = MainActivity.currentMainActivity.get();
				if (main != null) {
					main.scriptPrintCallback(str);
				}
			}
		}

		@JSFunction
		public double getPlayerX() {
			return nativeGetPlayerLoc(AXIS_X);
		}
		@JSFunction
		public double getPlayerY() {
			return nativeGetPlayerLoc(AXIS_Y);
		}
		@JSFunction
		public double getPlayerZ() {
			return nativeGetPlayerLoc(AXIS_Z);
		}

		@JSFunction
		public NativePointer getPlayerEnt() {
			playerEnt.value = nativeGetPlayerEnt();
			return playerEnt;
		}
		@JSFunction
		public NativePointer getLevel() {
			return new NativePointer(nativeGetLevel()); //TODO: WTF does this do?
		}

		@JSFunction
		public void setPosition(NativePointer ent, double x, double y, double z) {
			nativeSetPosition(ent.value, (float) x, (float) y, (float) z);
		}

		@JSFunction
		public void setVelX(NativePointer ent, double amount) {
			nativeSetVel(ent.value, (float) amount, AXIS_X);
		}
		@JSFunction
		public void setVelY(NativePointer ent, double amount) {
			nativeSetVel(ent.value, (float) amount, AXIS_Y);
		}
		@JSFunction
		public void setVelZ(NativePointer ent, double amount) {
			nativeSetVel(ent.value, (float) amount, AXIS_Z);
		}

		@JSFunction
		public void explode(double x, double y, double z, double radius) {
			nativeExplode((float) x, (float) y, (float) z, (float) radius);
		}

		@JSFunction
		public void addItemInventory(int id, int amount) {
			nativeAddItemInventory(id, amount);
		}

		@JSFunction
		public void rideAnimal(NativePointer /*Flynn*/rider, NativePointer mount) {
			nativeRideAnimal(rider.value, mount.value);
		}

		@JSFunction
		public void spawnChicken(double x, double y, double z, String tex) { //Textures not supported
			nativeSpawnEntity((float) x, (float) y, (float) z, 10);
		}

		@JSFunction
		public void spawnCow(double x, double y, double z, String tex) { //Textures not supported
			nativeSpawnEntity((float) x, (float) y, (float) z, 11);
		}

		@JSFunction
		public int getCarriedItem() {
			return nativeGetCarriedItem();
		}

		@JSFunction
		public void preventDefault() {
			nativePreventDefault();
		}

		@JSFunction
		public void setTile(int x, int y, int z, int id) {
			nativeSetTile(x, y, z, id, 0);
		}

		//standard methods introduced in API level 0.2
		@JSFunction
		public void clientMessage(String text) {
			print(text); //TODO: proper client message support
		}

		@JSFunction
		public void setNightMode(boolean isNight) {
			nativeSetNightMode(isNight);
		}

		@JSFunction
		public int getTile(int x, int y, int z) {
			return nativeGetTile(x, y, z);
		}

		@JSFunction
		public void setPositionRelative(NativePointer ent, double x, double y, double z) {
			nativeSetPositionRelative(ent.value, (float) x, (float) y, (float) z);
		}

		@JSFunction
		public void setRot(NativePointer ent, double yaw, double pitch) {
			nativeSetRot(ent.value, (float) yaw, (float) pitch);
		}
		//@JSFunction
		//public void setTile(int x, int y, int z, int id, int damage) {
		//	nativeSetTile(x, y, z, id, damage);
		//}

		//standard methods introduced in API level 0.3
		@JSFunction
		public double getPitch(NativePointer ent) {
			if (ent == null) ent = getPlayerEnt();
			return nativeGetPitch(ent.value);
		}

		@JSFunction
		public double getYaw(NativePointer ent) {
			if (ent == null) ent = getPlayerEnt();
			return nativeGetYaw(ent.value);
		}
	}

	private static class NativePointer extends ScriptableObject {
		public long value;
		public NativePointer(long value) {
			this.value = value;
		}
		@Override
		public String getClassName() {
			return "NativePointer";
		}
	}
}