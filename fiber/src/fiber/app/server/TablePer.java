package fiber.app.server;

import java.util.Map;

import fiber.common.LockPool;
import fiber.common.Marshaller;
import fiber.db.Walker;
import fiber.io.Log;
import fiber.io.Octets;
import fiber.io.OctetsStream;
import fiber.io.Timer;
import fiber.mapdb.TValue;
import fiber.mapdb.Table;
import fiber.mapdb.WKey;

public class TablePer extends Table {
	public final static class RemoveExpirePolicy implements ShrinkPolicy {
		private final int expireDuration;
		public RemoveExpirePolicy(int expireDuration) {
			this.expireDuration = expireDuration;
		}
		@Override
		public boolean check(Object key, TValue value) {
			return value.getLastAccessTime() + this.expireDuration < Timer.currentTime();
		}		
	}
	
	private final static RemoveExpirePolicy DEFAULT_POLICY = new RemoveExpirePolicy(600);

	public TablePer(int id, int maxsize, Marshaller msKey,
			Marshaller msValue, ShrinkPolicy policy) {
		super(id, true, maxsize, msKey, msValue, policy);
	}
	
	public TablePer(int id, int maxsize, Marshaller msKey,
			Marshaller msValue) {
		super(id, true, maxsize, msKey, msValue, DEFAULT_POLICY);
	}

	@Override
	protected Object loadValue(Object key) throws Exception {
		OctetsStream os = OctetsStream.create(8);
		this.marshalKey(os, key);
		Octets ovalue = G.storage.get(this.getId(), os.toOctets());
		if(ovalue == null) return null;
		OctetsStream vos = OctetsStream.wrap(ovalue);
		Object value = this.unmarshalValue(vos);
		return value;
	}
	
	
	@Override
	public void walk(final Walk w) {
		G.storage.walk(this.getId(), new Walker() {
			@Override
			public boolean onProcess(Octets key, Octets value) {
				try {
					Object okey = unmarshalKey(OctetsStream.wrap(key));
					Object ovalue = unmarshalValue(OctetsStream.wrap(value));
					return w.onProcess(TablePer.this, okey, new TValue(ovalue));
				} catch(Exception e) {
					Log.err("TablePer.walk. exception:%s", e);
					e.printStackTrace();
					return true;
				}
			}
		});
		
	}

	@Override
	public void shrink() {
		LockPool pool = LockPool.getInstance();
		ShrinkPolicy policy = this.getPolicy();
		for(Map.Entry<Object, TValue> e : this.getDataMap().entrySet()) {
			Object key = e.getKey();
			TValue value = e.getValue();
			// skip dirty datas.
			if(policy.check(key, value)) {
				int lockid = pool.lockid(WKey.keyHashCode(this.getId(), key));
				pool.lock(lockid);
				try {
					if(policy.check(key, value) && !Transaction.isDirty(new WKey(this, key))) {
						remove(key);
					}	
				} finally {
					pool.unlock(lockid);
				}
			}
		}
	}

	@Override
	public void walkCache(Walk w) {
		for(Map.Entry<Object, TValue> e : this.getDataMap().entrySet()) {
			if(!w.onProcess(this, e.getKey(), e.getValue())) return;
		}
	}

}
