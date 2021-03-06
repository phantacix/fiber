package fiber.db;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fiber.common.Marshaller;
import static fiber.io.Log.log;
import fiber.io.MarshalException;
import fiber.io.OctetsStream;

public abstract class Table {
	private final ConcurrentHashMap<Object, TValue> map;
	private final int id;
	private final boolean persist;
	private final int maxsize;
	private final Marshaller msKey;
	private final Marshaller msValue;
	private final ShrinkPolicy policy;

	public Table(int id, boolean persist, int maxsize, Marshaller msKey, Marshaller msValue, ShrinkPolicy policy) {
		this.id = id;
		this.persist = persist;
		this.maxsize = maxsize;
		this.map = new ConcurrentHashMap<Object, TValue>();
		this.msKey = msKey;
		this.msValue = msValue;
		this.policy = policy;
	}
	
	public final int getId() { return id; }
	public final boolean isPersist() { return persist; }
	public final int size() { return map.size(); }
	public final int maxsize() { return this.maxsize; }
	public final boolean overmaxsize() { return size() > maxsize(); }
	protected int remainSizeAfterShrink() { return maxsize() * 4 / 5; }
	
	public final Map<Object, TValue> getDataMap() { return map; }
	protected final ShrinkPolicy getPolicy() { return policy; }
	
	public TValue get(Object key) throws Exception {
		TValue value = map.get(key);
		if(value == null) {
			TValue newValue = loadValue(key);
			value = map.putIfAbsent(key, newValue);
			return value != null ? value : newValue;
		} else {
			return value;
		}
	}
	public Object put(Object key, TValue value) { return map.put(key, value); }
	public Object putIfAbsent(Object key, TValue value) {	return map.putIfAbsent(key, value);	}
	
	public void onUpdate(Object key, TValue value) { }
	protected TValue loadValue(Object key) throws Exception { return new TValue(null); }
	
	public static interface Walk {
		abstract boolean onProcess(Table table, Object key, TValue value);
	}
	
	abstract public void walk(Walk w);
	abstract public void walkCache(Walk w);
	
	/**
	 * make sure no other threads operate on it.
	 * you can lock it before opertion.
	 */
	public void remove(Object key) {
		TValue value = this.map.remove(key);
		if(value != null) {
			log.debug("Table:{} remove key:{} value:{}", this.getId(), key, value);
			value.setShrink(true);
		}
	}
	
	public static interface ShrinkPolicy {
		/**
		 * 
		 * @return  if we need to remove this entry, return true, else return false 
		 */
		boolean check(Object key, TValue value); 
	}
	
	public void shrink() {
		LockPool pool = LockPool.getInstance();
		ShrinkPolicy policy = this.getPolicy();
		int toRemoveNum = this.size() - this.remainSizeAfterShrink();
		for(Map.Entry<Object, TValue> e : this.getDataMap().entrySet()) {
			Object key = e.getKey();
			TValue value = e.getValue();
			if(policy.check(key, value)) {
				int lockid = pool.lockid(WKey.keyHashCode(this.getId(), key));
				pool.lock(lockid);
				try {
					// double check.
					if(policy.check(key, value)) {
						remove(key);
						if(--toRemoveNum <= 0) break;
					}	
				} finally {
					pool.unlock(lockid);
				}
			}
		}
	}
	
	public final void marshalKey(OctetsStream os, Object key) {
		this.msKey.marshal(os, key);
	}
	public final Object unmarshalKey(OctetsStream os) throws MarshalException {
		return this.msKey.unmarshal(os);
	}
	public final void marshalValue(OctetsStream os, Object value) {
		this.msValue.marshal(os, value);
	}
	public final Object unmarshalValue(OctetsStream os) throws MarshalException {
		return this.msValue.unmarshal(os);
	}

}
