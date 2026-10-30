-- HTTP versions of six Uysot read tools, added NEXT TO the code-backed ones, switched off by default.
--
-- Why beside and not instead: the built-in searchFlats also records the client's roomsNeeded / budgetRange
-- and remembers the offered flats in client memory, and the debt tools register their figures for FactGuard
-- in code; an HTTP tool cannot do the first two. So nothing is replaced: the platform admin tests these in
-- /platform/integration-tools, then switches a built-in off and its HTTP twin on, per company or by default.
--
-- Requests are the ones the code made; item fields are the ones the code read. The list's wrapper is not
-- documented, so every mapping tries data / data.data / data.content (the code did the same).
-- Named differently from their built-in twin on purpose: two functions with one name cannot be offered.

INSERT INTO integration_tool (provider, tool_key, kind, tool_group, label, description, permission, scope,
                              default_enabled, sort_order, definition)
VALUES
('UYSOT', 'SEARCH_FLATS_LIVE', 'HTTP', 'FLATS', 'Xonadon qidirish (HTTP)',
 'Sotuvdagi xonadonlar va narxlar — to''g''ridan-to''g''ri Uysot so''rovi.', 'CONTRACT', 'READ', FALSE, 210,
 $json$
{
  "name": "searchFlatsLive",
  "description": "Searches flats ON SALE in the company's residential complexes, cheapest first. Call it when the client asks what is available, about prices, room counts, floors or a budget. To limit it to a complex, first call listComplexesLive and pass that complex's id in houseIds. Say ONLY flats and prices returned from here - never invent a flat, price or area.",
  "http": {
    "url": "v1/open-api/flat/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 5},
        "orders": {"type": "object", "x-source": "static", "x-value": {"PRICE": "ASC"}},
        "statuses": {"type": "array", "x-source": "static", "x-value": ["SALE"]},
        "rooms": {"type": "array", "items": {"type": "string"}, "description": "number of rooms the client wants, as text in a list, e.g. [\"2\"]"},
        "priceFrom": {"type": "number", "description": "minimum price as a plain number, in the complex's currency"},
        "priceTo": {"type": "number", "description": "the client's maximum budget as a plain number, in the complex's currency"},
        "houseIds": {"type": "array", "items": {"type": "integer"}, "description": "ids of the residential complexes to look in, from listComplexesLive"},
        "floorFrom": {"type": "integer", "description": "lowest acceptable floor"},
        "floorTo": {"type": "integer", "description": "highest acceptable floor"}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "flats": "$.data[*]{id,houseName,buildingName,number,rooms,area,floor,price,priceNotRepaired,priceRepaired,pricePerArea,currencyCcy,status} || $.data.data[*]{id,houseName,buildingName,number,rooms,area,floor,price,priceNotRepaired,priceRepaired,pricePerArea,currencyCcy,status} || $.data.content[*]{id,houseName,buildingName,number,rooms,area,floor,price,priceNotRepaired,priceRepaired,pricePerArea,currencyCcy,status}"
  },
  "maxCallsPerCall": 6,
  "effect": "READ"
}
$json$::jsonb),
('UYSOT', 'LIST_COMPLEXES_LIVE', 'HTTP', 'FLATS', 'Turar joy majmualari (HTTP)',
 'Majmualar ro''yxati: manzil, qavatlar va topshirish muddati — to''g''ridan-to''g''ri Uysot so''rovi.', 'CONTRACT', 'READ', FALSE, 220,
 $json$
{
  "name": "listComplexesLive",
  "description": "Lists the company's residential complexes with their address, floors and planned hand-over date. isSale is true for a complex whose flats are on sale: offer only those. Call it when the client asks which projects there are, where they are or when they will be finished, and to get a complex's id for searchFlatsLive.",
  "http": {
    "url": "v1/open-api/house/all",
    "method": "GET",
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "complexes": "$.data[*]{id,name,isSale,address,floor,release} || $.data.data[*]{id,name,isSale,address,floor,release} || $.data.content[*]{id,name,isSale,address,floor,release}"
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb),
('UYSOT', 'GET_BOOKING_STATUS_LIVE', 'HTTP', 'BOOKINGS', 'Bron holati (HTTP)',
 'Mijozning bronlari: xonadon, holat va muddat — to''g''ridan-to''g''ri Uysot so''rovi.', 'BOOKING', 'READ', FALSE, 230,
 $json$
{
  "name": "getBookingLive",
  "description": "Reads the client's bookings from the CRM: the flat, its status and the booking deadline. Call it when the client asks whether their flat is still held or until when. State ONLY what is returned from here.",
  "http": {
    "url": "v1/open-api/booking/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 5},
        "search": {"type": "string", "x-source": "variable", "x-value": "phone_last9"}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "bookings": "$.data[*]{id,house.name,houseName,flat.number,flatNumber,number,status,bookingStatus,expireDate,expiresAt,endDate,deadline,amount,bookingAmount,currency.ccy,currencyCcy} || $.data.data[*]{id,house.name,houseName,flat.number,flatNumber,number,status,bookingStatus,expireDate,expiresAt,endDate,deadline,amount,bookingAmount,currency.ccy,currencyCcy} || $.data.content[*]{id,house.name,houseName,flat.number,flatNumber,number,status,bookingStatus,expireDate,expiresAt,endDate,deadline,amount,bookingAmount,currency.ccy,currencyCcy}"
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb),
('UYSOT', 'GET_CONTRACT_BALANCE_LIVE', 'HTTP', 'DEBTS', 'Shartnoma qoldig''i (HTTP)',
 'Shartnoma summasi, to''langani va qolgan qarz — to''g''ridan-to''g''ri Uysot so''rovi.', 'CONTRACT', 'READ', FALSE, 240,
 $json$
{
  "name": "getContractLive",
  "description": "Reads a contract from the CRM by its number: total, paid, remaining balance (residue), days overdue (delay) and status. Call it when the client names a contract number and asks how much is left. Without a contract number call getDebtsLive instead. State ONLY the figures returned from here.",
  "http": {
    "url": "v1/open-api/contract/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "required": ["search"],
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 5},
        "statuses": {"type": "array", "x-source": "static", "x-value": ["ACTIVE", "STARTED"]},
        "search": {"type": "string", "description": "the contract number the client named, exactly as they said it"}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "contracts": "$.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.content[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status}"
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb),
('UYSOT', 'GET_DEBT_SUMMARY_LIVE', 'HTTP', 'DEBTS', 'Qarzdorlik (HTTP)',
 'Mijozning faol shartnomalari bo''yicha qarz va kechikish — to''g''ridan-to''g''ri Uysot so''rovi.', 'CONTRACT', 'READ', FALSE, 250,
 $json$
{
  "name": "getDebtsLive",
  "description": "Reads ALL of the client's active contracts and what is owed on each: the remaining balance (residue), days overdue (delay) and status. Call it when the client asks how much they owe in total or the debt is the reason for the call. Talk about the contract with the largest delay first. State ONLY the figures returned from here.",
  "http": {
    "url": "v1/open-api/contract/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 20},
        "statuses": {"type": "array", "x-source": "static", "x-value": ["ACTIVE", "STARTED"]},
        "search": {"type": "string", "x-source": "variable", "x-value": "phone_last9"}
      }
    },
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "contracts": "$.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.data[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status} || $.data.content[*]{id,number,client.name,clientName,amount,payedAmount,paidAmount,residue,currency.ccy,currencyCcy,delay,status}"
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb),
('UYSOT', 'GET_PAYMENT_SCHEDULE_LIVE', 'HTTP', 'DEBTS', 'To''lov jadvali (HTTP)',
 'Shartnomaning oylik to''lovlari: sana va summa — to''g''ridan-to''g''ri Uysot so''rovi.', 'CONTRACT', 'READ', FALSE, 260,
 $json$
{
  "name": "getPaymentsLive",
  "description": "Reads one contract's monthly payments from the CRM: date, amount and what is still unpaid. Needs the contract's id: take it from the id of the contract returned by getContractLive or getDebtsLive. Call it when the client asks when the next payment is or which payments are overdue. State ONLY the dates and amounts returned from here.",
  "http": {
    "url": "v1/open-api/contract/{contractId}/monthly-payment",
    "method": "GET",
    "pathParams": [
      {"name": "contractId", "type": "integer", "valueType": "LLM_PROMPT", "description": "the contract's id, from getContractLive or getDebtsLive", "required": true}
    ],
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "payments": "$.data[*]{date,paymentDate,dueDate,amount,unpaidAmount,residue,remaining,payment,paidAmount,payedAmount,paid,paidStatus,status} || $.data.data[*]{date,paymentDate,dueDate,amount,unpaidAmount,residue,remaining,payment,paidAmount,payedAmount,paid,paidStatus,status} || $.data.content[*]{date,paymentDate,dueDate,amount,unpaidAmount,residue,remaining,payment,paidAmount,payedAmount,paid,paidStatus,status}"
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb);
