/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package x7.repository.redis;

import x7.core.config.Configs;
import x7.core.util.VerifyUtil;
import x7.repository.exception.PersistenceException;

/**
 * 缓存解决，三级缓存
 * @author sim
 *
 */
public class CacheResolver3 {

	public final static String NANO_SECOND = ".N_S";
	
	private static CacheResolver3 instance = null;
	public static CacheResolver3 getInstance(){
		if (instance == null){
			instance = new CacheResolver3();
		}
		return instance;
	}
	
	/**
	 * 标记缓存要更新
	 * @param clz
	 * @return nanuTime_String
	 */
	@SuppressWarnings("rawtypes")
	public String markForRefresh(Class clz){
		
		String key = getNSKey(clz);
		String time = String.valueOf(System.nanoTime());
		JedisConnector_Cache3.getInstance().set(key.getBytes(), time.getBytes());
		return time;
	}
	
	
	/**
	 * 简单的，低效的缓存结果<br>
	 * 高效请调用setResultKeyList()方法， FIXME
	 * @param clz
	 * @param condition
	 * @param obj
	 */
	@SuppressWarnings("rawtypes")
	public void setResult(Class clz, String condition, Object obj){
		String key = getKey(clz, condition);
		System.out.println("save key: " + key);
		int validSecond = Configs.getIntValue("x7.cache.second") / 2;
		try {
			JedisConnector_Cache3.getInstance().set(key.getBytes(), ObjectUtil.toBytes(obj), validSecond);
		} catch (Exception e) {
			throw new PersistenceException(e.getMessage());
		}
	}
	
	/**
	 * 简单的，低效的获取缓存结果<br>
	 * 高效请调用getResultKeyList()方法， FIXME
	 * @param clz
	 * @param condition
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public Object getResult(Class clz, String condition){
		String key = getKey(clz, condition);
		System.out.println("get key: " + key);
		byte[] bytes = JedisConnector_Cache3.getInstance().get(key.getBytes());
		
		if (bytes == null)
			return null;
		
		return ObjectUtil.toObject(bytes, clz);

	}
	
	
	@SuppressWarnings("rawtypes")
	private String getNSKey(Class clz){
		return clz.getName()+"_"+ NANO_SECOND;
	}
	

	
	@SuppressWarnings("rawtypes")
	private String getKey(Class clz, String condition){
		return VerifyUtil.toMD5(getPrefix(clz) + condition);
	}

	
	/**
	 * 获取缓存KEY前缀
	 * @param clz
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private String getPrefix(Class clz){
		String key = getNSKey(clz);
		byte[] nsArr = JedisConnector_Cache3.getInstance().get(key.getBytes());
		if (nsArr == null){
			String str = markForRefresh(clz);
			return clz.getName() + str;
		}
		return clz.getName() + new String(nsArr);
	}


/////////// 以下是联合主键类专用的三级缓存， 用于缓存keyOne相关的/////////////////

	
	/**
	 * 标记缓存要更新
	 * @param clz
	 * @return nanuTime_String
	 */
	@SuppressWarnings("rawtypes")
	public String markForRefresh(Class clz, long idOne){
		
		markForRefresh(clz);
		
		String key = getNSKey(clz, idOne);
		String time = String.valueOf(System.nanoTime());
		JedisConnector_Cache3.getInstance().set(key.getBytes(), time.getBytes());
		return time;
	}
	
	
	/**
	 * 简单的，低效的缓存结果<br>
	 * 高效请调用setResultKeyList()方法， FIXME
	 * @param clz
	 * @param condition
	 * @param obj
	 */
	@SuppressWarnings("rawtypes")
	public void setResult(Class clz, long idOne, String condition, Object obj){
		String key = getKey(clz, idOne, condition);
		System.out.println("save key: " + key);
		int validSecond = Configs.getIntValue("x7.cache.second");
		try {
			JedisConnector_Cache3.getInstance().set(key.getBytes(), ObjectUtil.toBytes(obj), validSecond);
		} catch (Exception e) {
			throw new PersistenceException(e.getMessage());
		}
	}
	
	/**
	 * 简单的，低效的获取缓存结果<br>
	 * 高效请调用getResultKeyList()方法， FIXME
	 * @param clz
	 * @param condition
	 * 
	 */
	@SuppressWarnings("rawtypes")
	public Object getResult(Class clz, long idOne, String condition){
		String key = getKey(clz, idOne, condition);
		System.out.println("get key: " + key);
		byte[] bytes = JedisConnector_Cache3.getInstance().get(key.getBytes());
		
		if (bytes == null)
			return null;
		
		return ObjectUtil.toObject(bytes, clz);

	}
	
	
	@SuppressWarnings("rawtypes")
	private String getNSKey(Class clz, long idOne){
		return clz.getName()+"_"+idOne+"_"+ NANO_SECOND;
	}

	
	@SuppressWarnings("rawtypes")
	private String getKey(Class clz, long idOne, String condition){
		return VerifyUtil.toMD5(getPrefix(clz, idOne) + condition);
	}

	
	/**
	 * 获取缓存KEY前缀
	 * @param clz
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private String getPrefix(Class clz,long idOne){
		
		String key = getNSKey(clz, idOne);
		byte[] nsArr = JedisConnector_Cache3.getInstance().get(key.getBytes());
		if (nsArr == null){
			String str = markForRefresh(clz, idOne);
			return clz.getName() +idOne+ str;
		}
		return clz.getName() +idOne+ new String(nsArr);
	}


}
