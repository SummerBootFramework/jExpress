package org.summerboot.jexpress.util.format;


import org.jspecify.annotations.NonNull;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.ServiceError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServiceErrorSanitizer {

    public static final int ERR_FIELD_ERROR_CODE = 1;
    public static final int ERR_FIELD_ERROR_TAG = 1 << 1;
    public static final int ERR_FIELD_ERROR_DESC = 1 << 2;
    public static final int ERR_FIELD_CAUSE = 1 << 3;
    public static final int ERR_FIELD_INTERNAL_INFO = 1 << 4;
    public static final int ERR_FIELD_ADDITIONAL_FIELDS = 1 << 5;
    public static final int ERR_FIELD_ALL = ERR_FIELD_ERROR_CODE
            | ERR_FIELD_ERROR_TAG
            | ERR_FIELD_ERROR_DESC
            | ERR_FIELD_CAUSE
            | ERR_FIELD_INTERNAL_INFO
            | ERR_FIELD_ADDITIONAL_FIELDS;

    private ServiceErrorSanitizer() {
    }

//    public static ServiceError deepClone(ServiceError source) {
//        return deepClone(source, true, ERR_FIELD_ALL);
//    }

    public static ServiceError deepClone(ServiceError source, boolean includeAdditionalFields, int errorFieldBitmap) {
        if (source == null) {
            return null;
        }

        ServiceError target = new ServiceError(source.getRef());
        if (includeAdditionalFields) {
            cloneAdditionalFields(source.getAdditionalFields(), target);
        }

        if (errorFieldBitmap != 0) {
            List<Err> sourceErrors = source.getErrors();
            if (sourceErrors != null && !sourceErrors.isEmpty()) {
                List<Err> copiedErrors = new ArrayList<>(sourceErrors.size());
                for (Err sourceErr : sourceErrors) {
                    if (sourceErr == null) {
                        //copiedErrors.add(null);
                        continue;
                    }
                    //Err copiedErr = new Err(sourceErr.getErrorCode(), sourceErr.getErrorTag(), sourceErr.getErrorDesc(), sourceErr.getCause(), sourceErr.getInternalInfo());
                    Err copiedErr = getErr(errorFieldBitmap, sourceErr);
                    if ((errorFieldBitmap & ERR_FIELD_ADDITIONAL_FIELDS) != 0) {
                        cloneAdditionalFields(sourceErr.getAdditionalFields(), copiedErr);
                    }
                    copiedErrors.add(copiedErr);
                }
                target.setErrors(copiedErrors);
            }
        }

        return target;
    }

    private static @NonNull Err getErr(int errorFieldBitmap, Err sourceErr) {
        Err copiedErr = new Err();
        if ((errorFieldBitmap & ERR_FIELD_ERROR_CODE) != 0) {
            copiedErr.setErrorCode(sourceErr.getErrorCode());
        }
        if ((errorFieldBitmap & ERR_FIELD_ERROR_TAG) != 0) {
            copiedErr.setErrorTag(sourceErr.getErrorTag());
        }
        if ((errorFieldBitmap & ERR_FIELD_ERROR_DESC) != 0) {
            copiedErr.setErrorDesc(sourceErr.getErrorDesc());
        }
        if ((errorFieldBitmap & ERR_FIELD_CAUSE) != 0) {
            copiedErr.setCause(sourceErr.getCause());
        }
        if ((errorFieldBitmap & ERR_FIELD_INTERNAL_INFO) != 0) {
            copiedErr.setInternalInfo(sourceErr.getInternalInfo());
        }
        return copiedErr;
    }

    private static void cloneAdditionalFields(Map<String, Object> sourceAdditionalFields, org.summerboot.jexpress.webserver.jackson.AdditionalJsonFields target) {
        if (sourceAdditionalFields == null || sourceAdditionalFields.isEmpty() || target == null) {
            return;
        }
        sourceAdditionalFields.forEach((key, value) -> target.adAdditionalField(key, deepCopyValue(value)));
    }

    private static Object deepCopyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(k, deepCopyValue(v)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        if (value instanceof Object[] array) {
            Object[] copy = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                copy[i] = deepCopyValue(array[i]);
            }
            return copy;
        }
        return value;
    }
}