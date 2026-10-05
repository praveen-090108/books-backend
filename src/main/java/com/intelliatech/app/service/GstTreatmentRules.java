package com.intelliatech.app.service;

import com.intelliatech.app.entity.GstTreatment;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

@Component
public class GstTreatmentRules {
    private static final EnumSet<GstTreatment> SOURCE_REQUIRED = EnumSet.of(
            GstTreatment.REGISTERED_BUSINESS_REGULAR,
            GstTreatment.REGISTERED_BUSINESS_COMPOSITION,
            GstTreatment.SPECIAL_ECONOMIC_ZONE,
            GstTreatment.DEEMED_EXPORT,
            GstTreatment.TAX_DEDUCTOR,
            GstTreatment.TAX_COLLECTOR);

    public boolean requiresSourceOfSupply(GstTreatment treatment) {
        return treatment != null && SOURCE_REQUIRED.contains(treatment);
    }
}
