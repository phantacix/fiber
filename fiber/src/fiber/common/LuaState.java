package fiber.common;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.luajc.LuaJC;

public class LuaState {
	public static Globals create() {
		Globals g = JsePlatform.standardGlobals();
		LuaJC.install(g);
		return g;
	}
	
	public static Globals create(String luaFile, String searchPath) {
		Globals g = create();
		addSearchPath(g, searchPath);
		if(!luaFile.isEmpty())
			g.loadfile(luaFile).call();
		return g;
	}
	
	public static void addSearchPath(Globals g, String searchPath) {
		LuaValue pkg = g.get("package");
		pkg.set("path", String.format("%s;%s/?.lua", pkg.get("path").tostring(), searchPath));
	}
	
	public static void main(String[] args) {
		Globals g = create("e:/t.lua", "e:/");
		g.set("abc", "def");
	}
}
