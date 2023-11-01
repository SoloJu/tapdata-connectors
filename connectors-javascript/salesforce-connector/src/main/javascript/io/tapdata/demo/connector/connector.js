config.setStreamReadIntervalSeconds(60);
var batchStart = Date.parse(new Date());
var afterData;
var clientInfo = {
    "client_id": "3MVG9n_HvETGhr3Ai83SSfHGaxjpZakxMv8ZB8yl5vP.6NMlgXFAhVcuqtruP9ehJxEGrmZnH6fvlhHA6yjE.",
    "client_secret": "BD2C1E4178343B85CAEEAA574528F8B5EFFC8C36DEC0139D31097CA1DE7A5751",
    "url": "https://login.salesforce.com",
    "version": "57.0"
}

function discoverSchema(connectionConfig) {
    return [
        {
            "name": 'Contact',
            "fields": {
                'Id': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false,
                    'isPrimaryKey': true,
                    'primaryKeyPos': 1
                },
                'AccountId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'AssistantName': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'AssistantPhone': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Birthdate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'CleanStatus': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Department': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Description': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Email': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'EmailBouncedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'EmailBouncedReason': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Fax': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'FirstName': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'HomePhone': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'IndividualId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'IsDeleted': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'IsEmailBounced': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'Jigsaw': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Languages__c': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastActivityDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastName': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'LastReferencedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastViewedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LeadSource': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MailingCity': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MailingCountry': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MailingGeocodeAccuracy': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MailingLatitude': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MailingLongitude': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MailingPostalCode': {
                    'type': 'Number',
                    'comment': '',
                    'nullable': true
                },
                'MailingState': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MailingStreet': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MasterRecordId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MobilePhone': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Name': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherCity': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherCountry': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherGeocodeAccuracy': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherLatitude': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherLongitude': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherPhone': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherPostalCode': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherState': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OtherStreet': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OwnerId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Phone': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'PhotoUrl': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'RecordTypeId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'ReportsToId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Salutation': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'Title': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastModifiedById': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastModifiedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'CreatedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                }
            }
        },
        {
            "name": 'Opportunity',
            "fields": {
                'Id': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false,
                    'isPrimaryKey': true,
                    'primaryKeyPos': 1
                },
                'AccountId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'Amount': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'CampaignId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'CloseDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'ContactId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Description': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'ExpectedRevenue': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Fiscal': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'FiscalQuarter': {
                    'type': 'Number',
                    'comment': '',
                    'nullable': true
                },
                'FiscalYear': {
                    'type': 'Number',
                    'comment': '',
                    'nullable': true
                },
                'ForecastCategory': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'ForecastCategoryName': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'HasOpenActivity': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'HasOpportunityLineItem': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'HasOverdueTask': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'IsClosed': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'IsDeleted': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'IsWon': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'LastActivityDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastAmountChangedHistoryId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastCloseDateChangedHistoryId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastReferencedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastStageChangeDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastViewedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LeadSource': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Name': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'NextStep': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'OwnerId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Pricebook2Id': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'PushCount': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'RecordTypeId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'StageName': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'TotalOpportunityQuantity': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Type': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastModifiedById': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastModifiedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'CreatedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                }
            }
        },
        {
            "name": 'Lead',
            "fields": {
                'Id': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false,
                    'isPrimaryKey': true,
                    'primaryKeyPos': 1
                },
                'AnnualRevenue': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'City': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'CleanStatus': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Company': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'CompanyDunsNumber': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'ConvertedAccountId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'ConvertedContactId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'ConvertedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'ConvertedOpportunityId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Country': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Description': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Email': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'EmailBouncedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'EmailBouncedReason': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Fax': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'FirstName': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'GeocodeAccuracy': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'IndividualId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Industry': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'IsConverted': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'IsDeleted': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'IsUnreadByOwner': {
                    'type': 'Boolean',
                    'comment': '',
                    'nullable': true
                },
                'Jigsaw': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastActivityDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastName': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastReferencedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Latitude': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Longitude': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LeadSource': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MasterRecordId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'MobilePhone': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Name': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'NumberOfEmployees': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'PhotoUrl': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'PostalCode': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'RecordTypeId': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Salutation': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'State': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Status': {
                    'type': 'String',
                    'comment': '',
                    'nullable': false
                },
                'Street': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Title': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'Website': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastModifiedById': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'LastModifiedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                },
                'CreatedDate': {
                    'type': 'String',
                    'comment': '',
                    'nullable': true
                }
            }
        }
    ];
}

function batchRead(connectionConfig, nodeConfig, offset, tableName, pageSize, batchReadSender) {
    let invoke;
    let result = [];
    let isFirst = true;
    let pageInfo = {"hasNextPage": true};
    do {
        try {
            if (isFirst) {
                invoke = invoker.invoke(tableName, clientInfo);
            } else {
                clientInfo.after = afterData;
                invoke = invoker.invoke(tableName + " by after", clientInfo);
            }
        } catch (e) {
            throw ("Failed to query the data. Please check the connection." + JSON.stringify(invoke));
        }

        if (!checkHttpResult(invoke.result.data, tableName)) {
            return;
        }
        let uiApi = invoke.result.data.uiapi;
        let queryTable = uiApi.query[tableName + ""];
        let queryEdges = queryTable.edges;
        pageInfo = queryTable.pageInfo;

        afterData = pageInfo.endCursor;
        disassemblyData(queryEdges, result);
        isFirst = false;
        batchReadSender.send(result, tableName, {}, false);
        result = [];
        batchStart = Number(Date.parse(new Date()));
    } while (pageInfo.hasNextPage && isAlive());
}

function checkHttpResult(data, tableName) {
    let uiApi = data.uiapi;
    if ('undefined' === uiApi || null == uiApi){
        log.warn("Query result not contains param which name is 'uiapi', result: {}", data);
        return false;
    }

    let resultQuery = uiApi.query;
    if ('undefined' === resultQuery || null == resultQuery){
        log.warn("Query result not contains param which name is 'uiapi.query', result: {}", data);
        return false;
    }

    let queryTable = resultQuery[tableName + ""];
    if ('undefined' === queryTable || null == queryTable) {
        log.warn("Query result not contains param which name is 'uiapi.query.{}', result: {}", tableName, data);
        return false;
    }

    let queryEdges = queryTable.edges;
    if ('undefined' === queryEdges || null == queryEdges) {
        log.warn("Query result not contains param which name is 'uiapi.query.{}.edges', or this param not array value, result: {}", tableName, data);
        return false;
    }

    let queryFirstEdge = queryEdges[0];
    if ('undefined' === queryFirstEdge || null == queryFirstEdge) {
        log.warn("Query result not contains param which name is 'uiapi.query.{}.edges[0]', result: {}", tableName, data);
        return false;
    }

    let pageInfo = queryTable.pageInfo;
    if ('undefined' === pageInfo || null == pageInfo) {
        log.warn("Query result not contains param which name is 'uiapi.query.{}.pageInfo', result: {}", tableName, data);
        return false;
    }
    return true;
}

function streamRead(connectionConfig, nodeConfig, offset, tableNameList, pageSize, streamReadSender) {
    if (!checkParam(tableNameList)) return;

    for (let index = 0; index < tableNameList.length; index++) {
        if (!isAlive()) break;
        let pageInfo = {"hasNextPage": true};
        let arr = [];
        let invoke;
        let first = true;
        let tableName = tableNameList[index];
        do {
            if (!isAlive()) break;
            try {
                if (first) {
                    invoke = invoker.invoke(tableName + " stream read", clientInfo);
                } else {
                    clientInfo.after = afterData;
                    invoke = invoker.invoke(tableName + " stream read by after", clientInfo);
                }
            } catch (e) {
                throw ("Failed to query the data. Please check the connection." + JSON.stringify(invoke));
            }
            if (!checkHttpResult(invoke.result.data, tableName)) {
                return;
            }
            let resultMap = invoke.result.data.uiapi.query[tableName + ""];
            let resultData = resultMap.edges;
            pageInfo = resultMap.pageInfo;
            afterData = pageInfo.endCursor;
            if (resultData[0] && resultData.length > 0) {
                for (let j = 0; j < resultData.length; j++) {
                    if (!isAlive()) break;
                    let resultItem = resultData[j];
                    let nod = resultItem.node;
                    if (Number(new Date(nod.LastModifiedDate.value)) > Number(new Date(batchStart))) {
                        if (nod.LastModifiedDate.value === nod.CreatedDate.value) {
                            arr[j] = {
                                "eventType": "i",
                                "tableName": tableName,
                                "afterData": sendData(nod),
                                "referenceTime": Number(new Date())
                            };
                        } else {
                            arr[j] = {
                                "eventType": "u",
                                "tableName": tableName,
                                "afterData": sendData(nod),
                                "referenceTime": Number(new Date())
                            };
                        }
                        batchStart = Date.parse(new Date());
                    } else {
                        batchStart = Date.parse(new Date());
                        break;
                    }
                }
                streamReadSender.send(arr, tableNameList[index], {});
                arr = [];
                first = false;
            }
        } while (pageInfo.hasNextPage && isAlive())
    }
}

function commandCallback(connectionConfig, nodeConfig, commandInfo) {
    if (commandInfo.command === 'OAuth') {
        let getToken = invoker.invokeWithoutIntercept("get access token", clientInfo);
        if (getToken.result) {
            // connectionConfig.access_token = getToken.result.access_token;
            connectionConfig.refresh_token = getToken.result.refresh_token;
            connectionConfig.Authorization = getToken.result.access_token;
            connectionConfig._endpoint = getToken.result.instance_url;
        }
        return connectionConfig;
    }
}

function updateToken(connectionConfig, nodeConfig, apiResponse) {
    if (apiResponse.httpCode === 503 || apiResponse.result.length > 0 && apiResponse.result[0].errorCode && apiResponse.result[0].errorCode === "REQUEST_LIMIT_EXCEEDED") {
        throw (apiResponse.result[0].message)
    }
    if (apiResponse.httpCode === 401 || (apiResponse.result && apiResponse.result.length > 0 && apiResponse.result[0].errorCode && apiResponse.result[0].errorCode === 'INVALID_SESSION_ID')) {
        try {
            let getToken = invoker.invokeWithoutIntercept("refresh token", clientInfo);
            let httpCode = getToken.httpCode
            checkAuthority(getToken, httpCode);
            if (getToken && getToken.result && getToken.result.access_token) {
                connectionConfig.Authorization = getToken.result.access_token;
                return {"access_token": getToken.result.access_token};
            }
        } catch (e) {
            log.warn(e)
            throw(e);
        }
    }
    return null;
}

function connectionTest(connectionConfig) {
    let invoke;
    let httpCode;
    let items = [];
    let opportunityCode = 0;
    try {
        invoke = invoker.invoke('Opportunity', clientInfo);
        httpCode = invoke.httpCode;
        opportunityCode = exceptionUtil.statusCode(httpCode);
        items.push({
            "test": "Permission check: Opportunity",
            "code": opportunityCode,
            "result": result(invoke, httpCode)
        });
    } catch (e) {
        items.push({
            "test": "Permission check: Opportunity",
            "code": -1,
            "result": exceptionUtil.eMessage(e)
        });
    }

    let contactCode = 0;
    try{
        invoke = invoker.invoke('Contact', clientInfo);
        httpCode = invoke.httpCode;
        contactCode = exceptionUtil.statusCode(httpCode);
        items.push({
            "test": "Permission check: Contact",
            "code": contactCode,
            "result": result(invoke, httpCode)
        });
    } catch (e) {
        items.push({
            "test": "Permission check: Contact",
            "code": -1,
            "result": exceptionUtil.eMessage(e)
        });
    }

    let leadCode = 0;
    try{
        invoke = invoker.invoke('Lead', clientInfo);
        httpCode = invoke.httpCode;
        leadCode = exceptionUtil.statusCode(httpCode);
        items.push({
            "test": "Permission check: Lead",
            "code": leadCode,
            "result": result(invoke, httpCode)
        });
    } catch (e) {
        items.push({
            "test": "Permission check: Lead",
            "code": -1,
            "result": exceptionUtil.eMessage(e)
        });
    }

    if (!(opportunityCode !== 1 && contactCode !== 1 && leadCode !== 1)) {
        let str = opportunityCode===1 && contactCode === 1 && leadCode === 1? "Pass with Opportunity, Contact and Lead" : (
            "Pass only: " +
            ( opportunityCode===1? "Opportunity, ":"") +
            ( opportunityCode===1? "Contact, ":"") +
            ( opportunityCode===1? "Lead, ":"") +
            "and " +
            ( opportunityCode===1? "Opportunity, ":"") +
            ( opportunityCode===1? "Contact, ":"") +
            ( opportunityCode===1? "Lead, ":"") +" not be support"
        )
        //if support stream, please push read log and read
        items.push({
            "test": "Read log",
            "code": 1,
            "result": str
        });
        items.push({
            "test": "Read",
            "code": 1,
            "result": str
        });
    } else {
        items.push({
            "test": "Not any table be supported",
            "code": 0,
            "result": "Opportunity, Contact and Lead not be support"
        });
    }
    return items;
}

