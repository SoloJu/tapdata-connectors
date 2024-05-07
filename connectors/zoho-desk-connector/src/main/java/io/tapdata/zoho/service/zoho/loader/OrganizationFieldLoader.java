package io.tapdata.zoho.service.zoho.loader;

import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.zoho.entity.HttpEntity;
import io.tapdata.zoho.entity.HttpNormalEntity;
import io.tapdata.zoho.entity.HttpResult;
import io.tapdata.zoho.entity.HttpType;
import io.tapdata.zoho.enums.FieldModelType;
import io.tapdata.zoho.enums.HttpCode;
import io.tapdata.zoho.utils.Checker;
import io.tapdata.zoho.utils.ZoHoHttp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrganizationFieldLoader extends ZoHoStarter implements ZoHoBase {
    public static final String API_NAME = "apiName";
    public static final String DATA = "data";
    protected OrganizationFieldLoader(TapConnectionContext tapConnectionContext) {
        super(tapConnectionContext);
    }

    public static OrganizationFieldLoader create(TapConnectionContext tapConnectionContext) {
        return new OrganizationFieldLoader(tapConnectionContext);
    }

    public HttpResult allOrganizationFields(FieldModelType model) {
        if (Checker.isEmpty(model)) {
            return null;
        }
        HttpNormalEntity header = requestHeard();
        HttpEntity form = HttpEntity.create().build("module", model.getModel());
        ZoHoHttp http = ZoHoHttp.create(String.format(ZO_HO_BASE_URL, "/api/v1/organizationFields"), HttpType.GET, header).form(form);
        HttpResult httpResult = this.readyAccessToken(http);
        String code = httpResult.getCode();
        if (HttpCode.SUCCEED.getCode().equals(code)) {
            Map<String, Object> resultObj = (Map<String, Object>) httpResult.getResult();
            if (Checker.isEmpty(resultObj)) return null;
            Object fieldListObj = resultObj.get(DATA);
            if (Checker.isEmpty(fieldListObj)) return null;
            return httpResult;
        }
        return null;
    }

    /**
     * 获取当前组织下的系统字段和自定义字段
     **/
    public Map<String, Map<String, Object>> organizationFields(FieldModelType model) {
        Map<String, Map<String, Object>> fieldMap = new HashMap<>();
        HttpResult get = this.allOrganizationFields(model);
        if (!Checker.isEmpty(get)) {
            List<Map<String, Object>> fieldArr = (List<Map<String, Object>>) (((Map<String, Object>) get.getResult()).get(DATA));
            fieldMap = fieldArr.stream().collect(Collectors.toMap(field -> (String) field.get(API_NAME), field -> field));
        }
        return fieldMap;
    }

    /**
     * 获取当前组织下的自定义字段
     **/
    public Map<String, Map<String, Object>> customFieldMap(FieldModelType model) {
        Map<String, Map<String, Object>> fieldMap = new HashMap<>();
        HttpResult get = this.allOrganizationFields(model);
        if (!Checker.isEmpty(get)) {
            List<Map<String, Object>> fieldArr = (List<Map<String, Object>>) (((Map<String, Object>) get.getResult()).get(DATA));
            /**
             * {
             *     "data": [
             *         {
             *             "displayLabel": "Department",
             *             "apiName": "departmentId",
             *             "isCustomField": false,
             *             "showToHelpCenter": true,
             *             "i18NLabel": "部门",
             *             "name": "departmentId",
             *             "isEncryptedField": false,
             *             "id": "10504000000000273",
             *             "type": "LookUp",
             *             "maxLength": 50,
             *             "isMandatory": false
             *         },
             *          {
             *             "displayLabel": "整数 1",
             *             "apiName": "cf_zheng_shu_1",
             *             "isCustomField": true,
             *             "showToHelpCenter": true,
             *             "i18NLabel": "整数 1",
             *             "name": "整数 1",
             *             "isEncryptedField": false,
             *             "id": "10504000000165379",
             *             "type": "Number",
             *             "maxLength": 1,
             *             "isMandatory": false
             *         }
             *      ]
             * }
             * */
            fieldMap = fieldArr.stream().filter(field -> (Boolean) field.get("isCustomField"))
                    .collect(Collectors.toMap(field -> (String) field.get(API_NAME), field -> field));
        }
        return fieldMap;
    }

    @Override
    public TapConnectionContext getContext() {
        return null;
    }
}
