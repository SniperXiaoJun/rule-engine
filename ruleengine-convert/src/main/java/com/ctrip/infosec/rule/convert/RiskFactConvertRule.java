package com.ctrip.infosec.rule.convert;

import com.ctrip.infosec.common.model.RiskFact;
import com.ctrip.infosec.configs.event.*;
import com.ctrip.infosec.configs.utils.Utils;
import com.ctrip.infosec.rule.convert.config.InternalConvertConfigHolder;
import com.ctrip.infosec.rule.convert.internal.DataUnit;
import com.ctrip.infosec.rule.convert.internal.InternalRiskFact;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.meidusa.toolkit.common.util.collection.ArrayHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jizhao on 2015/6/15.
 */
public class RiskFactConvertRule {

    private static Logger logger = LoggerFactory.getLogger(RiskFactConvertRule.class);

    private String eventPoint;
    private List<FieldMapping> mappings;

    private static int LIST_TYPE = 2;
    private static int OBJECT_TYPE = 1;

    /**
     * key 是target 的 a.b.c 中的 a或b或c
     */
//    private Map<String,DataUnitDefinition> unitDefinitionMap=new HashMap<String, DataUnitDefinition>();
//    private static  List<FieldMapping> fieldMappingList;
    public InternalRiskFact apply(RiskFact riskFact) {
        /**
         * dataUnitMapping 的 key 是 dataUnitDefinition 的 id
         * 不管DataUnit中的data 是 list 还是 单个 object 全部放在 list<Map<Stirng,Object>> 中
         * 根据 DataUnit.DataUnitDefinition.type 的类型来决定 是取list[0] 还是 遍历list
         */
        Map<String, DataUnit> dataUnitMapping = new HashMap<String, DataUnit>();
        if (!InternalConvertConfigHolder.getRiskFactDefinitionConfigMap().containsKey(riskFact.getEventPoint())) {
            logger.warn("业务点" + riskFact.getEventPoint() + "所对应的InternalRiskFactDefinitionConfig未找到！！");
            return null;
        }
        InternalRiskFactDefinitionConfig internalRiskFactDefinitionConfig = InternalConvertConfigHolder.getRiskFactDefinitionConfigMap().get(riskFact.getEventPoint());
        List<DataUnitDefinition> dataUnitDefinitions = internalRiskFactDefinitionConfig.getDataUnitMetas();
        if (dataUnitDefinitions == null) {
            logger.warn("业务点" + riskFact.getEventPoint() + "所对应的DataUnitDefinition未找到！！");
            return null;
        }
        for (DataUnitDefinition definition : dataUnitDefinitions) {
            DataUnit dataUnit = new DataUnit();
            dataUnit.setDefinition(definition);
//
            if (dataUnit.getDefinition().getType() == OBJECT_TYPE) {
                dataUnit.setData(new HashMap<String, Object>());
            } else {
                dataUnit.setData(new ArrayList<Map<String, Object>>());
            }
            dataUnitMapping.put(definition.getMetadata().getName(), dataUnit);
        }


        /**
         * 设置基础信息
         */
        InternalRiskFact internalRiskFact = new InternalRiskFact();
        internalRiskFact.setEventPoint(riskFact.getEventPoint());
        internalRiskFact.setEventId(riskFact.getEventId());
        internalRiskFact.setAppId(riskFact.getAppId());
        internalRiskFact.setDataUnits(new ArrayList<DataUnit>());
        /**
         * 至此 eventpoint 对应的DatUnit 空对象创建完成。
         */

        /**
         * 获得eventPoint对应的FieldMapping
         *
         */
        if (!InternalConvertConfigHolder.getRiskConvertMappings().containsKey(riskFact.getEventPoint())) {
            logger.warn("业务点" + riskFact.getEventPoint() + "所对应的RiskFactConvertRuleConfig未找到！！");
            return null;
        }
        RiskFactConvertRuleConfig riskFactConvertRuleConfigs = InternalConvertConfigHolder.getRiskConvertMappings().get(riskFact.getEventPoint());
        List<FieldMapping> fieldMappingList = riskFactConvertRuleConfigs.getMappings();
        if (fieldMappingList == null) {
            logger.warn("业务点" + riskFact.getEventPoint() + "所对应的FieldMapping未找到！！");
            return null;
        }

        /**
         * 至此eventPoint对应的FieldMapping获取完成
         *
         */

        /**
         * 开始遍历FieldMapping
         */
        recurseFieldMappingList(riskFact.eventBody, dataUnitMapping, fieldMappingList);
        for (String key : dataUnitMapping.keySet()) {
            internalRiskFact.getDataUnits().add(dataUnitMapping.get(key));
        }

        return internalRiskFact;
    }

    /**
     * @param eventBody
     * @param dataUnitMapping
     * @param fieldMappingList
     */
    private void recurseFieldMappingList(Map eventBody, Map<String, DataUnit> dataUnitMapping,
                                         List<FieldMapping> fieldMappingList) {
        for (FieldMapping fieldMapping : fieldMappingList) {
            String srcName = fieldMapping.getSourceFieldName();
            String trgName = fieldMapping.getTargetFieldName();
            ArrayList<String> trgNames = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().split(trgName));
//            ArrayList<String> srcNames = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().split(srcName));

            String name = trgNames.get(0);
            /**
             * 找到DataUnitMetadata名字和targetName对应的
             */
            DataUnit dataUnit = null;
            for (Map.Entry<String, DataUnit> entry : dataUnitMapping.entrySet()) {
                if (entry.getValue().getDefinition().getMetadata().getName().equals(name)) {
                    dataUnit = entry.getValue();
                    break;
                }
            }
            if (dataUnit != null) {
//                ArrayList<Map<String, Object>> data = (ArrayList<Map<String, Object>>) dataUnit.getData();
                Integer columnType = checkValidTrgName(trgNames.size() == 1 ? "" : trgName.substring(trgName.indexOf(".") + 1, trgName.length()), dataUnit.getDefinition().getMetadata());
                if (columnType != null) {


                    Object results = getValueFromMap(eventBody, srcName, dataUnit, dataUnit.getDefinition().getMetadata());

                    System.out.println("[trgName]:"+trgName+ "     [value]:" + Utils.JSON.toPrettyJSONString(results));


                    /**
                     * 将获取的results结果输入格式化如：
                     *
                     * {
                     "WalletWithdrawls" : {
                     "InfoSW" : {
                     "BankCardNo" : 3333333,
                     "IdNo" : 3333333,
                     "WithdrawType" : [ "AAA", "BBB", "CCC" ]
                     },
                     "WithdrawCardId" : "117.136.75.60"
                     }
                     }
                     *
                     */
                    convert2data(results, dataUnit, trgName);
//                    if (results instanceof List) {
//                        List<Object> objects = (List<Object>) results;
//                        for (Object result : objects) {
//                            Map<String,Object> reslutmapping=new HashMap<String, Object>();
//                            reslutmapping.put(trgName,result);
//                            data.add(reslutmapping);
//                        }
//                    } else {
//                        Map<String,Object> reslutmapping=new HashMap<String, Object>();
//                        reslutmapping.put(trgName,results);
//                        data.add(reslutmapping);
//                    }
                } else {
                    logger.warn("trgName对应的DataMetadata类型不符合当前版本要求");
                }
            } else {
                logger.warn("未找到Definition");
            }
        }
    }

    /**
     * 根据dataUnit 类型 创建map 或list<Map>
     * 先将trgNames中的a.b.c.***.n  第一个a取出 他对应的metadata的name
     * 比较特殊 其后进行递归  不考虑  单独a这种情况因为可以到这一步必定是符合条件的trgNames
     *
     * @param results
     * @param dataUnit
     * @param trgNames
     */
    private void convert2data(Object results, DataUnit dataUnit, String trgNames) {
        if (dataUnit.getDefinition().getType() == OBJECT_TYPE ) {
            Map data = (Map) dataUnit.getData();
            ArrayList<String> trgNameList = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().split(trgNames));
            String firstTrgName = trgNameList.get(0);
            trgNameList.remove(0);
            Object firstMap = data.get(firstTrgName);
            Object o = convert2InternalMapData(trgNameList, results, firstMap == null ? new HashMap<String, Object>() : (Map<String, Object>) firstMap, trgNames.substring(trgNames.indexOf(".") + 1, trgNames.length()), dataUnit);
            data.put(firstTrgName, o);
        }else  if(dataUnit.getDefinition().getType() == LIST_TYPE){



        }
    }

    private Map convert2InternalListData() {

        return null;
    }

    private Map convert2InternalMapData(ArrayList<String> trgNames, Object result, Map<String, Object> data, String trgName, DataUnit dataUnit) {
        System.out.println(trgNames.get(0));

        /**
         * 从完整targetname中获取 从头到当前 trgName[0]的前半段路径 。
         *
         */
        ArrayList<String> tempNames = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().split(trgName));
        String chechTypePath="";
        for(String name:tempNames){
            chechTypePath=chechTypePath.concat(name+".");
            if(name.equals(trgNames.get(0))){
                break;
            }
        }
        chechTypePath=chechTypePath.substring(0,chechTypePath.length()-1);

        Integer integer = this.checkColumnType(chechTypePath, dataUnit.getDefinition().getMetadata());
        String firstTrgName = trgNames.get(0);
        if (integer != null && integer != DataUnitColumnType.List.getIndex()) {
            Map<String, Object> map = new HashMap<String, Object>();

            if (trgNames.size() == 1) {
                map.put(firstTrgName, result);
                data.putAll(map);
            } else {
                trgNames.remove(0);
                Map subMap = (Map) data.get(firstTrgName);
                Map o = convert2InternalMapData(trgNames, result, subMap == null ? new HashMap<String, Object>() : subMap, trgName, dataUnit);
                map.put(firstTrgName, o);
                data.putAll(map);
            }
        }
        if (integer == DataUnitColumnType.List.getIndex()) {
            System.out.println("[key] : "+trgName+"[trgname] : "+trgNames.get(0)+"\n---------------list--------------------");
//            List<Object> list=new ArrayList<Object>();
            Map<String, Object> map = new HashMap<String, Object>();
            /**
             * 由于是trgName 中包含list 所以需要判断获得的对象是否也是list，
             * 又因为当前版本 最后一个column类型必定是简单类型 所以 result 中都是简单类型 循环处理一下就OK了
             * 是符合条件，不是的话   todo 呵呵再说
             *
             */
            if(result instanceof  List){
                List<Object> resultList= (List<Object>) result;
                trgNames.remove(0);
                List subList = (List) data.get(firstTrgName);
                if(subList==null){
                    subList=new ArrayList();
                }
                for(Object item: resultList){
                    Map submap = convert2InternalMapData(trgNames, item, new HashMap<String, Object>(), trgName, dataUnit);
                    subList.add(submap);
                }
                map.put(firstTrgName,subList);
                data.putAll(map);
            }
            else{

            }
        }
        return data;
    }


    private Integer checkColumnType(String trgName, DataUnitMetadata metadata) {
        if (StringUtils.isBlank(trgName) || metadata == null) {
            logger.warn("targNme 或者 metadata 为空");
            return null;
        }

        ArrayList<String> trgNames = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().split(trgName));

        DataUnitColumn dataUnitColumn = null;
        List<DataUnitColumn> columns = metadata.getColumns();
        for (DataUnitColumn column : columns) {
            if (column.getName().equals(trgNames.get(0))) {
                dataUnitColumn = column;
                break;
            }
        }
        if (dataUnitColumn == null) {
            return null;
        }

        /**
         * 最后一个 ColumnName
         */
        if (trgNames.size() == 1) {
            return dataUnitColumn.getColumnType();

        }
        /**
         * 不是最后一个ColumnName (trgNames.size()>1)
         */
        else {
            if (dataUnitColumn.getColumnType() == DataUnitColumnType.List.getIndex() || dataUnitColumn.getColumnType() == DataUnitColumnType.Object.getIndex()) {
                trgNames.remove(0);
                if (StringUtils.isNotBlank(dataUnitColumn.getNestedDataUnitMataNo())) {
                    DataUnitMetadata dataUnitMetadata = InternalConvertConfigHolder.getRiskFactMetadataMap().get(dataUnitColumn.getNestedDataUnitMataNo());
                    return checkValidTrgName(Joiner.on('.').join(trgNames), dataUnitMetadata);
                } else {
                    logger.warn("trgname:" + trgNames.get(0) + "作为object 和 list 对象没有找到 元数据Id");
                    return null;
                }
            } else {
                logger.warn("trgname:" + trgNames.get(0) + "不是最后columnName,columnType类型必须是List or Object");
                return null;
            }
        }
    }

    /**
     * 判断这个trgNames数组是否有效
     *
     * @param trgName
     * @param metadata
     * @return
     */
    private Integer checkValidTrgName(String trgName, DataUnitMetadata metadata) {
        if (StringUtils.isBlank(trgName) || metadata == null) {
            logger.warn("targNme 或者 metadata 为空");
            return null;
        }

        ArrayList<String> trgNames = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().split(trgName));

        DataUnitColumn dataUnitColumn = null;
        List<DataUnitColumn> columns = metadata.getColumns();
        for (DataUnitColumn column : columns) {
            if (column.getName().equals(trgNames.get(0))) {
                dataUnitColumn = column;
                break;
            }
        }
        if (dataUnitColumn == null) {
            return null;
        }

        /**
         * 最后一个 ColumnName
         */
        if (trgNames.size() == 1) {
            if (dataUnitColumn.getColumnType() != DataUnitColumnType.List.getIndex() && dataUnitColumn.getColumnType() != DataUnitColumnType.Object.getIndex()) {
                return dataUnitColumn.getColumnType();
            } else {
                logger.warn("未找到columnName：" + trgNames.get(0) + "或者columnType是list 或 object");
                return null;
            }
        }
        /**
         * 不是最后一个ColumnName (trgNames.size()>1)
         */
        else {
            if (dataUnitColumn.getColumnType() == DataUnitColumnType.List.getIndex() || dataUnitColumn.getColumnType() == DataUnitColumnType.Object.getIndex()) {
                trgNames.remove(0);
                if (StringUtils.isNotBlank(dataUnitColumn.getNestedDataUnitMataNo())) {
                    DataUnitMetadata dataUnitMetadata = InternalConvertConfigHolder.getRiskFactMetadataMap().get(dataUnitColumn.getNestedDataUnitMataNo());
                    return checkValidTrgName(Joiner.on('.').join(trgNames), dataUnitMetadata);
                } else {
                    logger.warn("trgname:" + trgNames.get(0) + "作为object 和 list 对象没有找到 元数据Id");
                    return null;
                }
            } else {
                logger.warn("trgname:" + trgNames.get(0) + "不是最后columnName,columnType类型必须是List or Object");
                return null;
            }
        }
    }

    private Object getValueFromMap(Map<String, Object> mapping, String sourceFieldName, DataUnit dataUnit, DataUnitMetadata metadata) {
        ArrayList<String> keys = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().limit(2).split(sourceFieldName));
        String key = keys.get(0);
        Object object = mapping.get(key);

        if (object instanceof Map) {
            if (keys.size() == 1) {
                logger.warn("取到的最后key:" + key + "的值是一个map 当前版本暂不支持，值被丢弃返回null");
                return null;
            } else {
                Object valueFromMap = getValueFromMap((Map) object, keys.get(1), dataUnit, metadata);


                return valueFromMap;
            }
        } else if (object instanceof List) {
            if (keys.size() == 1) {
                logger.warn("取到的最后key：" + key + "的值是一个List 当前版本暂不支持，值被丢弃返回null");
                return null;
            } else {
                return getValueFromList((List) object, keys.get(1), dataUnit, metadata);
            }
        } else {
            if (keys.size() > 1) {
                logger.warn("sourceFieldName未走到底！！当前key是：" + key + " value：" + object);
                return null;
            } else {
                return object;
            }
        }

    }

    private List getValueFromList(List<Object> list, String sourceFieldName, DataUnit dataUnit, DataUnitMetadata metadata) {
        ArrayList<String> keys = Lists.newArrayList(Splitter.on('.').omitEmptyStrings().trimResults().limit(2).split(sourceFieldName));
        Object tmpValue = null;
        List resultList = new ArrayList();
        for (Object item : list) {
            if (item instanceof Map) {
                tmpValue = getValueFromMap((Map<String, Object>) item, sourceFieldName, dataUnit, metadata);
//                if(tmpValue!=null){
                resultList.add(tmpValue);
//                }
//                if (keys.size() == 1) {
//                    tmpValue = getValueFromMap((Map<String, Object>) item, sourceFieldName);
//                    if (tmpValue != null) {
//                        resultList.add(tmpValue);
//                    }
//                }
//                else if(keys.size()>1){
//
//                }
//                else {
//                    logger.warn("取到的最后对象是一个map 当前版本暂不支持，值被丢弃");
//                }
            } else if (item instanceof List) {
                if (keys.size() > 1) {
                    tmpValue = getValueFromList((List<Object>) item, sourceFieldName, dataUnit, metadata);
//                    if (tmpValue != null) {
                    resultList.add(tmpValue);
//                    }
                } else {
                    logger.warn("取到的最后对象是一个List 当前版本暂不支持，值被丢弃");
                }
            } else {
                logger.warn("当前版本list下必须要map或者list,取到对的值" + tmpValue + "被丢弃");
            }
        }
        return resultList;
    }


    public String getEventPoint() {
        return eventPoint;
    }

    public void setEventPoint(String eventPoint) {
        this.eventPoint = eventPoint;
    }

    public List<FieldMapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<FieldMapping> mappings) {
        this.mappings = mappings;
    }

}