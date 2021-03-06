package act.db.beetlsql;

/*-
 * #%L
 * ACT Beetlsql
 * %%
 * Copyright (C) 2017 - 2019 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.Act;
import act.app.DbServiceManager;
import act.db.DB;
import act.db.DaoBase;
import act.db.DbService;
import org.beetl.sql.core.SQLManager;
import org.beetl.sql.core.SQLReady;
import org.beetl.sql.core.kit.BeanKit;
import org.osgl.util.E;

import java.util.*;

import static act.app.DbServiceManager.DEFAULT;


public class BeetlSqlDao<ID_TYPE, MODEL_TYPE> extends DaoBase<ID_TYPE, MODEL_TYPE, BeetlSqlQuery<MODEL_TYPE>> {

    SQLManager sqlManager;
    String idAttr;

    public BeetlSqlDao() {
        DB db = modelType().getAnnotation(DB.class);
        String svcId = null == db ? DEFAULT : db.value();
        DbServiceManager dbm = Act.app().dbServiceManager();
        DbService dbService = dbm.dbService(svcId);
        E.invalidConfigurationIf(null == dbService, "cannot find db service by id: %s", svcId);
        E.unexpectedIfNot(dbService instanceof BeetlSqlService, "expected BeetlSqlService, found: " + dbService.getClass().getSimpleName());
        setBeetlSqlService((BeetlSqlService) dbService);
    }

    public BeetlSqlDao(SQLManager sqlManager, String idAttr, Class<MODEL_TYPE> modelType, Class<ID_TYPE> idType) {
        super(idType, modelType);
        this.idAttr = idAttr;
        this.sqlManager = sqlManager;
    }


    @Override
    public MODEL_TYPE findById(ID_TYPE id) {
        return sqlManager.single(this.modelType(), id);
    }

    @Override
    public MODEL_TYPE findLatest() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MODEL_TYPE findLastModified() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<MODEL_TYPE> findByIdList(Collection<ID_TYPE> idList) {
        //TODO,优化成 "select * from tableName where id in "
        List<MODEL_TYPE> list = new ArrayList<>(idList.size());
        for (ID_TYPE id : idList) {
            list.add(findById(id));
        }
        return list;
    }

    @Override
    public MODEL_TYPE reload(MODEL_TYPE entity) {
        return this.sqlManager.unique(this.modelType(), this.getId(entity));
    }

    @Override
    public ID_TYPE getId(MODEL_TYPE entity) {

        ID_TYPE value = (ID_TYPE) BeanKit.getBeanProperty(entity, idAttr);
        return value;
    }

    @Override
    public MODEL_TYPE save(MODEL_TYPE entity) {
        sqlManager.upsert(entity);
        return entity;
    }

    @Override
    public void save(MODEL_TYPE entity, String fields, Object... values) {
        MODEL_TYPE dbEntity = this.reload(entity);
        StringTokenizer st = new StringTokenizer(fields, ":,; ");
        int index = 0;
        while (st.hasMoreTokens()) {
            BeanKit.setBeanProperty(dbEntity, values[index++], st.nextToken());
        }
        sqlManager.updateById(dbEntity);
    }

    @Override
    public List<MODEL_TYPE> save(Iterable<MODEL_TYPE> entities) {
        List list = new ArrayList();
        Iterator<MODEL_TYPE> it = entities.iterator();
        while (it.hasNext()) {
            MODEL_TYPE obj = it.next();
            sqlManager.insert(modelType(), obj, true);
            list.add(obj);
        }

        return list;
    }

    @Override
    public void delete(MODEL_TYPE entity) {
        this.sqlManager.deleteById(this.modelType(), this.getId(entity));
    }

    @Override
    public void delete(BeetlSqlQuery<MODEL_TYPE> query) {
        query.query.delete();
    }

    @Override
    public void deleteById(ID_TYPE id) {
        sqlManager.deleteById(this.modelType(), id);
    }

    @Override
    public void deleteBy(String fields, Object... values) throws IllegalArgumentException {
        BeetlSqlQuery<MODEL_TYPE> query = createQuery(fields, values);
        Iterator<MODEL_TYPE> it = query.fetch().iterator();
        while (it.hasNext()) {
            MODEL_TYPE entity = it.next();
            delete(entity);
        }

    }

    @Override
    public void deleteAll() {
        String tableName = this.sqlManager.getNc().getTableName(this.modelType());
        String sql = "delete from " + tableName;
        sqlManager.executeUpdate(new SQLReady(sql));
    }

    @Override
    public void drop() {
        throw new UnsupportedOperationException("BeetlSQL 不支持DDL 删除表");
    }

    @Override
    public BeetlSqlQuery<MODEL_TYPE> q() {
        return createQuery();
    }

    @Override
    public BeetlSqlQuery<MODEL_TYPE> createQuery() {
        return new BeetlSqlQuery<>(this, this.modelType());
    }

    @Override
    public BeetlSqlQuery<MODEL_TYPE> q(String fields, Object... values) {
        return createQuery(fields, values);
    }

    @Override
    public BeetlSqlQuery<MODEL_TYPE> createQuery(String fields, Object... values) {
        return new BeetlSqlQuery<>(this, this.modelType(), fields, values);
    }

    @Override
    public Object processLikeValue(String s) {
        return s.contains("%") ? s : "%" + s + "%";
    }

    private void setBeetlSqlService(BeetlSqlService beetl) {
        Class<MODEL_TYPE> modelType = modelType();
        this.idAttr = beetl.idColumn(modelType);
        this.sqlManager = beetl.beetlSql();
    }

}
