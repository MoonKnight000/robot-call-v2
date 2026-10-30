package uz.murodjon.robotcallv2.company.application.dto;

import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;

/** The telephony engine a company's calls run on, as the platform admin sees it. */
public record TelephonyEngineResponse(TelephonyEngine telephonyEngine) {
}
