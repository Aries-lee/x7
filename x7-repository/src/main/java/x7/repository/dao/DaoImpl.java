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
package x7.repository.dao;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.mysql.jdbc.Statement;

import x7.core.bean.BeanElement;
import x7.core.bean.Criteria;
import x7.core.bean.CriteriaBuilder;
import x7.core.bean.Parsed;
import x7.core.bean.Parser;
import x7.core.repository.X;
import x7.core.util.BeanMapUtil;
import x7.core.util.BeanUtil;
import x7.core.util.BeanUtilX;
import x7.core.util.JsonX;
import x7.core.util.StringUtil;
import x7.core.web.Pagination;
import x7.repository.ResultSetUtil;
import x7.repository.exception.RollbackException;
import x7.repository.mapper.Mapper;
import x7.repository.mapper.MapperFactory;

/**
 * 
 * @author Sim
 */
public class DaoImpl implements Dao {

	private static DaoImpl instance;

	public static DaoImpl getInstance() {
		if (instance == null) {
			instance = new DaoImpl();
		}
		return instance;
	}

	private DaoImpl() {
	}

	private DataSource dataSource;

	private DataSource dataSource_R;

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setDataSource_R(DataSource dataSource_R) {
		this.dataSource_R = dataSource_R;
	}

	private Connection getConnection(boolean isRead) throws SQLException {
		if (dataSource == null) {
			System.err.println("No DataSource");
		}
		if (dataSource_R == null) {

			if (!isRead) {
				if (!Tx.isNoBizTx()) {
					Connection connection = Tx.getConnection();
					if (connection == null) {
						connection = getConnection(dataSource);
						Tx.add(connection);
					}
					return connection;
				}
			}

			return getConnection(dataSource);
		}

		if (isRead) {
			return getConnection(dataSource_R);
		}

		if (!Tx.isNoBizTx()) {
			Connection connection = Tx.getConnection();
			if (connection == null) {
				connection = getConnection(dataSource);
				Tx.add(connection);
			}
			return connection;
		}

		return getConnection(dataSource);
	}

	private Connection getConnection(DataSource ds) throws SQLException {
		Connection c = ds.getConnection();

		if (c == null) {
			try {
				TimeUnit.MICROSECONDS.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return ds.getConnection();
		}

		return c;
	}

	/**
	 * 放回连接池,<br>
	 * 连接池已经重写了关闭连接的方法
	 */
	private static void close(Connection conn) {
		try {
			if (conn != null) {
				conn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static void close(PreparedStatement pstmt) {
		if (pstmt != null) {
			try {
				pstmt.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}


	@Override
	public boolean createBatch(List<Object> objList) {

		if (objList.isEmpty())
			return false;
		Object obj = objList.get(0);
		Class clz = obj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.CREATE);

		List<BeanElement> eles = MapperFactory.getElementList(clz);

		boolean isNoBizTx = false;
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			Parsed parsed = Parser.get(clz);

			String keyOne = parsed.getKey(X.KEY_ONE);

			Long keyOneValue = 0L;
			Field keyOneField = parsed.getKeyField(X.KEY_ONE);
			Class keyOneType = keyOneField.getType();
			if (keyOneType != String.class) {
				keyOneValue = parsed.getKeyField(X.KEY_ONE).getLong(obj);
			}

			/*
			 * 返回自增键
			 */

			conn = getConnection(false);

			conn.setAutoCommit(false);
			if (keyOneType != String.class && (keyOneValue == null || keyOneValue == 0)) {
				pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			} else {
				pstmt = conn.prepareStatement(sql);
			}

			isNoBizTx = Tx.isNoBizTx();
			if (!isNoBizTx) {
				Tx.add(pstmt);
			}

			for (Object o : objList) {

				int i = 1;

				for (BeanElement ele : eles) {

					Object value = ele.getMethod.invoke(o);
					if (value == null) {
						if (ele.clz == Boolean.class || ele.clz == Integer.class || ele.clz == Long.class
								|| ele.clz == Double.class || ele.clz == Float.class || ele.clz == BigDecimal.class
								|| ele.clz == Byte.class)
							value = 0;
						pstmt.setObject(i++, value);
					} else {

						if (ele.isJson) {
							String str = JsonX.toJson(value);
							pstmt.setObject(i++, str);
						} else {
							value = SqlUtil.filter(value);
							pstmt.setObject(i++, value);
						}

					}

				}

				pstmt.addBatch();

			}

			pstmt.executeBatch();

			if (isNoBizTx) {
				conn.commit();
			}

		} catch (Exception e) {
			System.out.println("Exception occured, while create: " + obj);
			e.printStackTrace();
			if (isNoBizTx) {
				try {
					conn.rollback();
					System.out.println("line 199" + e.getMessage());
					e.printStackTrace();

				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			} else {
				throw new RollbackException("RollbackException: " + e.getMessage());
			}
		} finally {
			if (isNoBizTx) {
				close(pstmt);
				close(conn);
			}
		}

		return true;
	}

	protected boolean remove(Object obj, Connection conn) {

		Class clz = obj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.REMOVE);

		boolean flag = false;
		boolean isNoBizTx = false;

		PreparedStatement pstmt = null;
		try {

			conn.setAutoCommit(false);
			pstmt = conn.prepareStatement(sql);

			isNoBizTx = Tx.isNoBizTx();
			if (!isNoBizTx) {
				Tx.add(pstmt);
			}

			Parsed parsed = Parser.get(clz);

			int i = 1;

			SqlUtil.adpterSqlKey(pstmt, parsed.getKeyField(X.KEY_ONE), null, obj, i);

			flag = pstmt.executeUpdate() == 0 ? false : true;

			if (isNoBizTx) {
				conn.commit();
			}

		} catch (Exception e) {
			e.printStackTrace();
			if (isNoBizTx) {
				try {
					conn.rollback();
					System.out.println("line 334 " + e.getMessage());
					e.printStackTrace();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			} else {
				throw new RollbackException("RollbackException: " + e.getMessage());
			}
		} finally {
			if (isNoBizTx) {
				close(pstmt);
				close(conn);
			}
		}

		return flag;
	}

	protected long create(Object obj, Connection conn) {
		long id = -1;

		Class clz = obj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.CREATE);

		List<BeanElement> eles = MapperFactory.getElementList(clz);

		boolean isNoBizTx = false;
		PreparedStatement pstmt = null;
		try {
			Parsed parsed = Parser.get(clz);
			String keyOne = parsed.getKey(X.KEY_ONE);

			Long keyOneValue = 0L;
			Field keyOneField = parsed.getKeyField(X.KEY_ONE);
			Class keyOneType = keyOneField.getType();
			if (keyOneType != String.class) {
				keyOneValue = parsed.getKeyField(X.KEY_ONE).getLong(obj);
			}

			/*
			 * 返回自增键
			 */

			conn.setAutoCommit(false);
			if (keyOneType != String.class && (keyOneValue == null || keyOneValue == 0)) {
				pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			} else {
				pstmt = conn.prepareStatement(sql);
			}

			isNoBizTx = Tx.isNoBizTx();
			if (!isNoBizTx) {
				Tx.add(pstmt);
			}

			int i = 1;
			for (BeanElement ele : eles) {

				Object value = ele.getMethod.invoke(obj);
				if (value == null) {
					if (ele.clz == Boolean.class || ele.clz == Integer.class || ele.clz == Long.class
							|| ele.clz == Double.class || ele.clz == Float.class || ele.clz == BigDecimal.class
							|| ele.clz == Byte.class)
						value = 0;
					pstmt.setObject(i++, value);
				} else {

					if (ele.isJson) {
						String str = JsonX.toJson(value);
						pstmt.setObject(i++, str);
					} if (ele.clz.isEnum()){
						String str = value.toString();
						pstmt.setObject(i++, str);
					}else {
						value = SqlUtil.filter(value);
						pstmt.setObject(i++, value);
					}

				}

			}

			pstmt.execute();

			if (keyOneType != String.class && (keyOneValue == null || keyOneValue == 0)) {
				ResultSet rs = pstmt.getGeneratedKeys();
				if (rs.next()) {
					id = rs.getLong(1);
				}

			} else {
				id = keyOneValue;
			}

			if (isNoBizTx) {
				conn.commit();
			}

		} catch (Exception e) {
			System.out.println("Exception occured, while create: " + obj);
			e.printStackTrace();
			if (isNoBizTx) {
				try {
					conn.rollback();
					e.printStackTrace();

				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			} else {
				throw new RollbackException("RollbackException: " + e.getMessage());
			}
		} finally {
			if (isNoBizTx) {
				close(pstmt);
				close(conn);
			}
		}

		return id;
	}

	protected boolean refresh(Object obj, Connection conn) {

		@SuppressWarnings("rawtypes")
		Class clz = obj.getClass();

		Parsed parsed = Parser.get(clz);

		Map<String, Object> queryMap = BeanUtilX.getRefreshMap(parsed, obj);

		String tableName = MapperFactory.getTableName(clz);
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ").append(tableName).append(" ");
		String sql = SqlUtil.concatRefresh(sb, parsed, queryMap);

		// System.out.println("refreshOptionally: " + sql);

		boolean flag = false;
		boolean isNoBizTx = false;
		PreparedStatement pstmt = null;
		try {
			conn.setAutoCommit(false);
			pstmt = conn.prepareStatement(sql);

			isNoBizTx = Tx.isNoBizTx();
			if (!isNoBizTx) {
				Tx.add(pstmt);
			}

			int i = 1;
			for (Object value : queryMap.values()) {
				value = SqlUtil.filter(value);
				pstmt.setObject(i++, value);
			}

			/*
			 * 处理KEY
			 */
			Field keyOneF = parsed.getKeyField(X.KEY_ONE);
			SqlUtil.adpterSqlKey(pstmt, keyOneF, null, obj, i);

			flag = pstmt.executeUpdate() == 0 ? false : true;

			if (isNoBizTx) {
				conn.commit();
			}

		} catch (Exception e) {
			e.printStackTrace();
			if (isNoBizTx) {
				try {
					conn.rollback();
					e.printStackTrace();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			} else {
				throw new RollbackException("RollbackException: " + e.getMessage());
			}
		} finally {
			if (isNoBizTx) {
				close(pstmt);
				close(conn);
			}
		}

		return flag;
	}

	@SuppressWarnings({ "rawtypes" })
	@Override
	public long create(Object obj) {

		Connection conn = null;
		try {
			conn = getConnection(false);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return create(obj, conn);
	}

	@Override
	public boolean refresh(Object obj) {

		Connection conn = null;
		try {
			conn = getConnection(false);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return refresh(obj, conn);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean remove(Object obj) {
		Connection conn = null;
		try {
			conn = getConnection(false);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return remove(obj, conn);
	}

	protected <T> T get(Class<T> clz, long idOne, Connection conn) {

		Parsed parsed = Parser.get(clz);

		List<T> list = new ArrayList<T>();

		String sql = MapperFactory.getSql(clz, Mapper.QUERY);
		List<BeanElement> eles = MapperFactory.getElementList(clz);

		PreparedStatement pstmt = null;
		BeanElement tempEle = null;
		try {
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;

			pstmt.setObject(i++, idOne);

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {
				while (rs.next()) {
					T obj = clz.newInstance();
					list.add(obj);
					initObj(obj, rs, tempEle, eles);
				}
			}

		} catch (Exception e) {

			e.printStackTrace();

			throw new RollbackException(
					"Exception occured by class = " + clz.getName() + ", property = " + tempEle.property);

		} finally {
			close(pstmt);
			close(conn);
		}

		if (list.isEmpty())
			return null;
		return list.get(0);
	}

	@Override
	public <T> T get(Class<T> clz, long idOne) {
		Connection conn = null;
		try {
			conn = getConnection(true);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return get(clz, idOne, conn);
	}

	protected List<Map<String, Object>> list(Class clz, String sql, List<Object> conditionList, Connection conn) {

		sql = sql.replace("drop", " ").replace("delete", " ").replace("insert", " ").replace(";", ""); // 手动拼接SQL,
																										// 必须考虑应用代码的漏

		Parsed parsed = Parser.get(clz);

		sql = BeanUtilX.mapper(sql, parsed);

		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

		PreparedStatement pstmt = null;

		try {
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;
			if (conditionList != null) {
				for (Object obj : conditionList) {
					pstmt.setObject(i++, obj);
				}
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {
				while (rs.next()) {
					Map<String, Object> mapR = new HashMap<String, Object>();
					list.add(mapR);
					ResultSetMetaData rsmd = rs.getMetaData();
					int count = rsmd.getColumnCount();
					for (i = 1; i <= count; i++) {
						String key = rsmd.getColumnLabel(i);
						String value = rs.getString(i);
						String property = parsed.getProperty(key);
						if (StringUtil.isNullOrEmpty(property)) {
							property = key;
						}
						mapR.put(property, value);
					}

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return list;
	}

	public List<Map<String, Object>> list(Class clz, String sql, List<Object> conditionList) {
		Connection conn = null;
		try {
			conn = getConnection(true);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}

		return list(clz, sql, conditionList, conn);
	}
	
	@Override
	public <T> List<T> list(Class<T> clz) {

		List<T> list = new ArrayList<T>();

		String sql = MapperFactory.getSql(clz, Mapper.LOAD);
		List<BeanElement> eles = MapperFactory.getElementList(clz);

		Connection conn = null;
		PreparedStatement pstmt = null;
		BeanElement tempEle = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {
				while (rs.next()) {
					T obj = clz.newInstance();
					list.add(obj);
					initObj(obj, rs, tempEle, eles);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RollbackException(
					"Exception occured by class = " + clz.getName() + ", property = " + tempEle.property);
		} finally {
			close(pstmt);
			close(conn);
		}

		return list;
	}

	@SuppressWarnings("rawtypes")
	public long getMaxId(Class clz) {

		long id = 0;

		String sql = MapperFactory.getSql(clz, Mapper.MAX_ID);

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			ResultSet rs = pstmt.executeQuery();
			if (rs != null) {
				if (rs.next()) {
					id = rs.getLong("maxId");
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return id;
	}

	protected <T> List<T> list(Object conditionObj, Connection conn) {

		Class clz = conditionObj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.LOAD);

		sql = sql.concat(" WHERE 1=1");

		Parsed parsed = Parser.get(clz);

		Map<String, Object> queryMap = BeanUtilX.getQueryMap(parsed, conditionObj);
		sql = SqlUtil.concat(parsed, sql, queryMap);

		// System.out.println("SyncDao.list(obj)...SQL: " + sql);

		List<T> list = new ArrayList<T>();

		PreparedStatement pstmt = null;
		BeanElement tempEle = null;
		try {
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;
			for (Object o : queryMap.values()) {
				pstmt.setObject(i++, o);
			}

			List<BeanElement> eles = parsed.getBeanElementList();
			ResultSet rs = pstmt.executeQuery();
			if (rs != null) {
				while (rs.next()) {
					T obj = (T) clz.newInstance();
					list.add(obj);
					initObj(obj, rs, tempEle, eles);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RollbackException(
					"Exception occured by class = " + clz.getName() + ", property = " + tempEle.property);
		} finally {
			close(pstmt);
			close(conn);
		}

		return list;
	}

	@Override
	public <T> List<T> list(Object conditionObj) {
		Connection conn = null;
		try {
			conn = getConnection(true);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return list(conditionObj, conn);
	}

	protected <T> Pagination<T> list(Criteria criteria, Pagination<T> pagination, Connection conn) {
		Class clz = criteria.getClz();

		List<Object> valueList = criteria.getValueList();

		String[] sqlArr = CriteriaBuilder.parse(criteria);

		String sqlCount = sqlArr[0];
		String sql = sqlArr[1];

		long count = 0;
		if (!pagination.isScroll()) {
			count = getCount(sqlCount, valueList);
		}

		pagination.setTotalRows(count);

		int page = pagination.getPage();
		int rows = pagination.getRows();
		int start = (page - 1) * rows;

		sql = Mapper.Dialect.Pagination.match(sql, start, rows);

		PreparedStatement pstmt = null;
		BeanElement tempEle = null;
		try {
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;
			for (Object obj : valueList) {
				pstmt.setObject(i++, obj);
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {

				List<BeanElement> eles = MapperFactory.getElementList(clz);

				while (rs.next()) {

					T obj = (T) clz.newInstance();
					pagination.getList().add(obj);
					initObj(obj, rs, tempEle, eles);

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RollbackException(
					"Exception occured by class = " + clz.getName() + ", property = " + tempEle.property);
		} finally {
			close(pstmt);
			close(conn);
		}

		return pagination;
	}

	@Override
	public <T> Pagination<T> list(Criteria criteria, Pagination<T> pagination) {

		Connection conn = null;
		try {
			conn = getConnection(true);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return list(criteria, pagination, conn);
	}

	@Override
	public Object getSum(Object conditionObj, String sumProperty) {

		Class<?> clz = conditionObj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.PAGINATION);

		Parsed parsed = Parser.get(clz);

		Map<String, Object> queryMap = BeanUtilX.getQueryMap(parsed, conditionObj);
		sql = SqlUtil.concat(parsed, sql, queryMap);

		String countSql = sql.replace(X.PAGINATION, "SUM(*) sum");
		countSql = countSql.replace("*", sumProperty);

		Object sum = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(countSql);

			int i = 1;
			for (Object o : queryMap.values()) {
				pstmt.setObject(i++, o);
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs.next()) {
				sum = rs.getObject("sum");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return sum;
	}

	@Override
	public Object getSum(String sumProperty, Criteria criteria) {

		Class<?> clz = criteria.getClz();
		Parsed parsed = Parser.get(clz);

		List<Object> valueList = criteria.getValueList();

		String[] sqlArr = CriteriaBuilder.parse(criteria);

		String sqlSum = sqlArr[2];

		sqlSum = sqlSum.replace(X.PAGINATION, "SUM(*) sum");
		if (StringUtil.isNotNull(sumProperty)) {
			sumProperty = parsed.getMapper(sumProperty);
			sqlSum = sqlSum.replace("*", sumProperty);
		}

		System.out.println(sqlSum);

		Object count = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sqlSum);

			int i = 1;
			for (Object o : valueList) {
				pstmt.setObject(i++, o);
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs.next()) {
				count = rs.getObject("sum");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return count;
	}

	/**
	 * Important getCount
	 * 
	 * @param sql
	 * @param set
	 * @return
	 */
	private long getCount(String sql, Collection<Object> set) {

		long count = 0;
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;
			for (Object obj : set) {
				pstmt.setObject(i++, obj);
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs.next()) {
				count = rs.getLong("count");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return count;
	}

	protected long getCount(Object conditionObj, Connection conn) {

		Class<?> clz = conditionObj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.PAGINATION);

		Parsed parsed = Parser.get(clz);

		Map<String, Object> queryMap = BeanUtilX.getQueryMap(parsed, conditionObj);
		sql = SqlUtil.concat(parsed, sql, queryMap);

		String countSql = sql.replace(X.PAGINATION, "COUNT(*) count");

		long count = 0;
		PreparedStatement pstmt = null;
		try {
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(countSql);

			int i = 1;
			for (Object o : queryMap.values()) {
				pstmt.setObject(i++, o);
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs.next()) {
				count = rs.getLong("count");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return count;
	}

	@Override
	public long getCount(Object conditionObj) {
		Connection conn = null;
		try {
			conn = getConnection(true);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return getCount(conditionObj, conn);
	}

	

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public <T> T getOne(T conditionObj, String orderBy, String sc) {

		Class clz = conditionObj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.LOAD);

		sql = sql.concat(" WHERE 1=1 ");

		Parsed parsed = Parser.get(clz);

		Map<String, Object> queryMap = BeanUtilX.getQueryMap(parsed, conditionObj);
		sql = SqlUtil.concat(parsed, sql, queryMap);

		String mapper = BeanUtilX.getMapper(orderBy);
		sql = sql + " order by " + mapper + " " + sc;
		sql = sql + " limit 1";

		// System.out.println("SyncDao.list(obj)...SQL: " + sql);

		List<Object> list = new ArrayList<Object>();

		Connection conn = null;
		PreparedStatement pstmt = null;
		BeanElement tempEle = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;
			for (Object o : queryMap.values()) {
				pstmt.setObject(i++, o);
			}

			List<BeanElement> eles = parsed.getBeanElementList();
			ResultSet rs = pstmt.executeQuery();
			if (rs != null) {
				while (rs.next()) {
					Object obj = clz.newInstance();
					list.add(obj);
					initObj(obj, rs, tempEle, eles);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RollbackException(
					"Exception occured by class = " + clz.getName() + ", property = " + tempEle.property);
		} finally {
			close(pstmt);
			close(conn);
		}

		if (list.isEmpty())
			return null;

		return (T) list.get(0);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public long getMaxId(Object conditionObj) {

		long id = 0;

		Class clz = conditionObj.getClass();

		String sql = MapperFactory.getSql(clz, Mapper.PAGINATION);

		Parsed parsed = Parser.get(clz);

		Map<String, Object> queryMap = BeanUtilX.getQueryMap(parsed, conditionObj);
		sql = SqlUtil.concat(parsed, sql, queryMap);

		sql = sql.replace(X.PAGINATION, "max(id) maxId");

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;

			for (Object o : queryMap.values()) {
				pstmt.setObject(i++, o);
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs.next()) {
				Object obj = rs.getObject("maxId");
				if (obj != null) {
					id = Long.valueOf(obj.toString());
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return id;
	}

	/**
	 * 没有特殊需求，请不要调用此代码
	 * 
	 * @param sql
	 */
	@Deprecated
	@Override
	public boolean execute(Object obj, String sql) {

		Parsed parsed = Parser.get(obj.getClass());

		sql = sql.replace("drop", " ").replace("delete", " ").replace("insert", " ").replace(";", ""); // 手动拼接SQL,
																										// 必须考虑应用代码的漏洞
		sql = BeanUtilX.mapper(sql, parsed);
		boolean b = false;
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(false);
			conn.setAutoCommit(false);
			pstmt = conn.prepareStatement(sql);

			b = pstmt.executeUpdate() == 0 ? false : true;
			conn.commit();
		} catch (Exception e) {
			e.printStackTrace();
			try {
				conn.rollback();
				System.out.println("line 1560 " + e.getMessage());
				e.printStackTrace();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		} finally {
			close(pstmt);
			close(conn);
		}

		return b;
	}

	protected boolean refresh(Object obj, Map<String, Object> conditionMap, Connection conn) {

		@SuppressWarnings("rawtypes")
		Class clz = obj.getClass();

		Parsed parsed = Parser.get(clz);

		Map<String, Object> queryMap = BeanUtilX.getRefreshMap(parsed, obj);

		String simpleName = BeanUtil.getByFirstLower(clz.getSimpleName());
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ").append(simpleName).append(" ");
		String sql = SqlUtil.concatRefresh(sb, parsed, queryMap, conditionMap);
		sql = BeanUtilX.mapperName(sql, parsed);

		// System.out.println("refreshOptionally: " + sql);

		boolean isNoBizTx = false;
		boolean flag = false;

		PreparedStatement pstmt = null;
		try {
			conn.setAutoCommit(false);
			pstmt = conn.prepareStatement(sql);

			isNoBizTx = Tx.isNoBizTx();
			if (!isNoBizTx) {
				Tx.add(pstmt);
			}

			int i = 1;
			for (Object value : queryMap.values()) {
				value = SqlUtil.filter(value);
				pstmt.setObject(i++, value);
			}

			/*
			 * 处理KEY
			 */
			Field keyOneF = parsed.getKeyField(X.KEY_ONE);
			SqlUtil.adpterRefreshCondition(pstmt, keyOneF, null, obj, i, conditionMap);

			flag = pstmt.executeUpdate() == 0 ? false : true;

			if (isNoBizTx) {
				conn.commit();
			}

		} catch (Exception e) {
			flag = false;
			e.printStackTrace();
			if (isNoBizTx) {
				try {
					conn.rollback();
					System.out.println("line 1675 " + e.getMessage());
					e.printStackTrace();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			} else {
				throw new RollbackException("RollbackException: " + e.getMessage());
			}
		} finally {
			if (isNoBizTx) {
				close(pstmt);
				close(conn);
			}
		}

		return flag;
	}

	@Override
	public boolean refresh(Object obj, Map<String, Object> conditionMap) {

		Connection conn = null;
		try {
			conn = getConnection(false);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}
		return refresh(obj, conditionMap, conn);
	}

	@Override
	public Object getCount(String countProperty, Criteria criteria) {

		Class<?> clz = criteria.getClz();

		Parsed parsed = Parser.get(clz);

		List<Object> valueList = criteria.getValueList();

		String[] sqlArr = CriteriaBuilder.parse(criteria);

		String sqlCount = sqlArr[2];

		sqlCount = sqlCount.replace(X.PAGINATION, "COUNT(*) count");
		if (StringUtil.isNotNull(countProperty)) {
			countProperty = parsed.getMapper(countProperty);
			sqlCount = sqlCount.replace("*", countProperty);
		}

		Object count = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sqlCount);

			int i = 1;
			for (Object o : valueList) {
				pstmt.setObject(i++, o);
			}
			ResultSet rs = pstmt.executeQuery();

			if (rs.next()) {
				count = rs.getObject("count");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		return count;
	}

	@Override
	public <T> List<T> in(Class<T> clz, List<? extends Object> inList) {

		Parsed parsed = Parser.get(clz);

		List<T> list = new ArrayList<T>();

		String sql = MapperFactory.getSql(clz, Mapper.LOAD);
		List<BeanElement> eles = MapperFactory.getElementList(clz);

		String keyOne = parsed.getKey(X.KEY_ONE);

		Field keyField = parsed.getKeyField(X.KEY_ONE);
		Class<?> keyType = keyField.getType();
		boolean isNumber = (keyType == long.class || keyType == int.class || keyType == Long.class
				|| keyType == Integer.class);

		String mapper = BeanUtilX.getMapper(keyOne);
		StringBuilder sb = new StringBuilder();
		sb.append(sql).append(" WHERE ").append(mapper);
		sb.append(" in (");

		int size = inList.size();
		if (isNumber) {
			for (int i = 0; i < size; i++) {
				Object id = inList.get(i);
				if (id == null)
					continue;
				sb.append(id);
				if (i < size - 1) {
					sb.append(",");
				}
			}
		} else {
			for (int i = 0; i < size; i++) {
				Object id = inList.get(i);
				if (id == null || StringUtil.isNullOrEmpty(id.toString()))
					continue;
				sb.append("'").append(id).append("'");
				if (i < size - 1) {
					sb.append(",");
				}
			}
		}

		sb.append(")");

		sql = sb.toString();

		System.out.println(sql);

		Connection conn = null;
		PreparedStatement pstmt = null;
		BeanElement tempEle = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {
				while (rs.next()) {
					T obj = clz.newInstance();
					list.add(obj);
					initObj(obj, rs, tempEle, eles);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RollbackException(
					"Exception occured by class = " + clz.getName() + ", property = " + tempEle.property);
		} finally {
			close(pstmt);
			close(conn);
		}

		return list;
	}

	@Override
	public <T> List<T> in(Class<T> clz, String inProperty, List<? extends Object> inList) {

		List<T> list = new ArrayList<T>();

		String sql = MapperFactory.getSql(clz, Mapper.LOAD);
		List<BeanElement> eles = MapperFactory.getElementList(clz);

		Parsed parsed = Parser.get(clz);

		BeanElement be = parsed.getElement(inProperty);
		if (be == null) {
			throw new RuntimeException(
					"Exception in method: <T> List<T> in(Class<T> clz, String inProperty, List<? extends Object> inList), no property: "
							+ inProperty);
		}
		Class<?> keyType = be.getMethod.getReturnType();
		boolean isNumber = (keyType == long.class || keyType == int.class || keyType == Long.class
				|| keyType == Integer.class);

		StringBuilder sb = new StringBuilder();
		String mapper = parsed.getMapper(inProperty);
		sb.append(sql).append(" WHERE ").append(mapper);
		sb.append(" in (");

		int size = inList.size();
		if (isNumber) {
			for (int i = 0; i < size; i++) {
				Object id = inList.get(i);
				if (id == null)
					continue;
				sb.append(id);
				if (i < size - 1) {
					sb.append(",");
				}
			}
		} else {
			for (int i = 0; i < size; i++) {
				Object id = inList.get(i);
				if (id == null || StringUtil.isNullOrEmpty(id.toString()))
					continue;
				sb.append("'").append(id).append("'");
				if (i < size - 1) {
					sb.append(",");
				}
			}
		}

		sb.append(")");

		sql = sb.toString();

		System.out.println(sql);

		Connection conn = null;
		PreparedStatement pstmt = null;
		BeanElement tempEle = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {
				while (rs.next()) {
					T obj = clz.newInstance();
					list.add(obj);
					initObj(obj, rs, tempEle, eles);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RollbackException(
					"Exception occured by class = " + clz.getName() + ", property = " + tempEle.property);
		} finally {
			close(pstmt);
			close(conn);
		}

		return list;
	}

	@Override
	public Pagination<Map<String, Object>> list(Criteria.Fetch criteriaJoinable,
			Pagination<Map<String, Object>> pagination) {

		Connection conn = null;
		try {
			conn = getConnection(true);
		} catch (SQLException e) {
			throw new RuntimeException("NO CONNECTION");
		}

		return this.list(criteriaJoinable, pagination, conn);
	}

	protected Pagination<Map<String, Object>> list(Criteria.Fetch criteriaFetch,
			Pagination<Map<String, Object>> pagination, Connection conn) {

		Class clz = criteriaFetch.getClz();

		List<Object> valueList = criteriaFetch.getValueList();

		String[] sqlArr = CriteriaBuilder.parse(criteriaFetch);

		String sqlCount = sqlArr[0];
		String sql = sqlArr[1];

		long count = 0;
		if (!pagination.isScroll()) {
			count = getCount(sqlCount, valueList);
		}
		pagination.setTotalRows(count);

		int page = pagination.getPage();
		int rows = pagination.getRows();
		int start = (page - 1) * rows;

		sql = Mapper.Dialect.Pagination.match(sql, start, rows);

		sql = sql.replace("*", criteriaFetch.getResultScript());

		System.out.println(sql);

		PreparedStatement pstmt = null;
		try {
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;
			for (Object obj : valueList) {
				pstmt.setObject(i++, obj);
			}

			List<String> resultKeyList = criteriaFetch.getResultList();
			if (resultKeyList.isEmpty()) {
				resultKeyList = criteriaFetch.listAllResultKey();
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {
				while (rs.next()) {
					Map<String, Object> mapR = new HashMap<String, Object>();
					pagination.getList().add(mapR);

					for (String property : resultKeyList) {
						String mapper = criteriaFetch.getFetchMapper().mapper(property);
						mapR.put(property, rs.getObject(mapper));
					}

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		List<Map<String, Object>> stringKeyMapList = pagination.getList();
		if (!stringKeyMapList.isEmpty()) {
			List<Map<String, Object>> jsonableMapList = BeanMapUtil.toJsonableMapList(stringKeyMapList);
			pagination.setList(jsonableMapList);
		}

		return pagination;
	}

	@Override
	public List<Map<String, Object>> list(Criteria.Fetch fetch) {

		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

		Class clz = fetch.getClz();

		List<Object> valueList = fetch.getValueList();

		String[] sqlArr = CriteriaBuilder.parse(fetch);

		String sql = sqlArr[1];

		sql = sql.replace("*", fetch.getResultScript());

		System.out.println(sql);

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection(true);
			conn.setAutoCommit(true);
			pstmt = conn.prepareStatement(sql);

			int i = 1;
			for (Object obj : valueList) {
				pstmt.setObject(i++, obj);
			}

			List<String> columnList = fetch.getResultList();
			if (columnList.isEmpty()) {
				columnList = fetch.listAllResultKey();// FIXME ALLWAYS BUG
			}

			ResultSet rs = pstmt.executeQuery();

			if (rs != null) {
				while (rs.next()) {
					Map<String, Object> mapR = new HashMap<String, Object>();
					list.add(mapR);

					for (String property : columnList) {
						String mapper = fetch.getFetchMapper().mapper(property);
						mapR.put(property, rs.getObject(mapper));
					}

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(pstmt);
			close(conn);
		}

		if (!list.isEmpty()) {
			List<Map<String, Object>> jsonableMapList = BeanMapUtil.toJsonableMapList(list);
			return jsonableMapList;
		}

		return list;
	}

	private <T> void initObj(T obj, ResultSet rs, BeanElement tempEle, List<BeanElement> eles)
			throws SQLException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		ResultSetUtil.initObj(obj, rs, tempEle, eles);
	}

	protected <T> T getOne(T conditionObj, Connection conn) {

		List<T> list = list(conditionObj, conn);

		if (list.isEmpty())
			return null;
		return list.get(0);
	}

	public class Monitor {

		public List<x7.repository.monitor.mysql.Process> showProcessList(boolean isRead) {
			String sql = "SHOW PROCESSLIST";

			List<Map<String, Object>> mapList = DaoImpl.getInstance().list(x7.repository.monitor.mysql.Process.class,
					sql, null);

			List<x7.repository.monitor.mysql.Process> objList = new ArrayList<x7.repository.monitor.mysql.Process>();

			for (Map<String, Object> map : mapList) {
				x7.repository.monitor.mysql.Process process = BeanMapUtil
						.toObject(x7.repository.monitor.mysql.Process.class, map);
				objList.add(process);
			}

			return objList;
		}
	}
}
