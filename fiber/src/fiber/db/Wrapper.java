package fiber.db;

import static fiber.io.Log.log;


public abstract class Wrapper<W> {
	public static abstract class Notifier {
		public abstract void onChange(Object o);
	}
	
	public static Notifier NONE_NOTIFIER = new Notifier() {
		public void onChange(Object o) { }
	};
	
	protected final W origin_data;
	protected W data;
	protected final Notifier notifier;
	
	public Wrapper(W w, Notifier n) {
		this.origin_data = w;
		this.data = this.origin_data;
		this.notifier = n;
	}
	
	@Override
	public String toString() {
		return  this.isModify() ? 
				String.format("%s{origin_data:%s data:%s}", this.getClass().getSimpleName(), this.origin_data, this.data) :
				String.format("%s{data:%s}", this.getClass().getSimpleName(), this.data);
	}
	
	public final boolean isModify() {
		return this.data != this.origin_data;
	}
	
	public final void checkModify() {
		if(!isModify()) {
			this.data = this.shallowClone();
			log.debug("Wrapper.checkModify. data:{}", this.data);
			notifier.onChange(this.data);
		}
	}
	
	/**
	 * 请在调用此接口前务必先设置新的data
	 */
	public final void forceModify() {
		//this.data = this.shallowClone();
		log.debug("Wrapper.forceModify. origin_data:{} data:{}", this.origin_data, this.data);
		notifier.onChange(this.data);
	}
	
	public final boolean isNULL() {
		return this.data == null;
	}
	
	public abstract W shallowClone();

	public final void assign(W w) {
		internalRefresh(w);
		notifier.onChange(w);
	}
	
	/**
	 * 
	 * @param w
	 * @desc  注意!如果想更新data, 使用assign, 而不是refresh！！！
	 */
	public void internalRefresh(W w) {
		this.data = w;
	}
	
	protected final W internalGetOriginData() {
		return this.origin_data;
	}
	
	protected final W internalGetData() {
		return this.data;
	}
	
	/** 
	 * 	开放接口给架构用的。普通用户千万不要试图使用此接口访问原始数据
	 */
	public static <V> V getOringinData(Wrapper<V> w) {
		return w.internalGetOriginData();
	}
	
	/** 
	 * 	开放接口给架构用的。普通用户千万不要试图使用此接口访问当前数据
	 */
	public static <V> V getData(Wrapper<V> w) {
		return w.internalGetData();
	}
}
