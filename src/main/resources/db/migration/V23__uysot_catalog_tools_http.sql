-- Uysot's flat search and complex list stop being code: they become HTTP catalog tools under the
-- SAME model names (searchFlats, listResidentialComplexes), so the agents' functions and the
-- scenarios that name them keep working. What the code did beside the request is now the tool's
-- own result settings (V22): the rooms and budget searched with become the call's roomsNeeded /
-- budgetRange, and the flats offered are remembered for the next call as offeredFlats.
--
-- Their HTTP twins (*_LIVE, V1) are deleted; a company's choice of a twin goes with it.

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "searchFlats",
  "description": "Searches flats ON SALE in the company's residential complexes, cheapest first. Call it when the client asks what is available, about prices, room counts, floors or a budget. To limit it to a complex, first call listResidentialComplexes and pass that complex's id in houseIds. Say ONLY flats and prices returned from here - never invent a flat, price or area.",
  "http": {
    "url": "v1/open-api/flat/filter",
    "method": "POST",
    "bodySchema": {
      "type": "object",
      "properties": {
        "page": {"type": "integer", "x-source": "static", "x-value": 1},
        "size": {"type": "integer", "x-source": "static", "x-value": 3},
        "orders": {"type": "object", "x-source": "static", "x-value": {"PRICE": "ASC"}},
        "statuses": {"type": "array", "x-source": "static", "x-value": ["SALE"]},
        "rooms": {"type": "array", "items": {"type": "string"}, "description": "number of rooms the client wants, as text in a list, e.g. [\"2\"]"},
        "priceFrom": {"type": "number", "description": "minimum price as a plain number, in the complex's currency"},
        "priceTo": {"type": "number", "description": "the client's maximum budget as a plain number, in the complex's currency"},
        "houseIds": {"type": "array", "items": {"type": "integer"}, "description": "ids of the residential complexes to look in, from listResidentialComplexes"},
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
  "result": {
    "maxItems": 3,
    "emptyMessage": "NO FLATS ON SALE MATCH - do not invent one. Ask whether another room count, budget or floor would suit, or offer a callback from a sales manager.",
    "errorMessage": "THE DATA IS NOT AVAILABLE RIGHT NOW - do not invent a name, price, flat or amount. This is not a question from the client: do not say you have no information, do not say you are an AI and do not transfer the call. Carry on with the conversation without this data - ask what matters to the client (district, budget, when they plan to buy) - and offer a visit or a callback from a specialist who will send the options.",
    "argumentVariables": {"rooms": "roomsNeeded", "priceTo": "budgetRange"},
    "memoryFacts": {"flats": "offeredFlats"}
  },
  "maxCallsPerCall": 6,
  "effect": "READ"
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'SEARCH_FLATS';

UPDATE integration_tool
SET kind              = 'HTTP',
    model_description = NULL,
    updated_at        = now(),
    definition        = $json$
{
  "name": "listResidentialComplexes",
  "description": "Lists the company's residential complexes with their address, floors and planned hand-over date. isSale is true for a complex whose flats are on sale: offer only those. Call it when the client asks which projects there are, where they are or when they will be finished, and to get a complex's id for searchFlats.",
  "http": {
    "url": "v1/open-api/house/all",
    "method": "GET",
    "responseTimeoutSecs": 6,
    "retryAttempts": 1
  },
  "responseMapping": {
    "complexes": "$.data[*]{id,name,isSale,address,floor,release} || $.data.data[*]{id,name,isSale,address,floor,release} || $.data.content[*]{id,name,isSale,address,floor,release}"
  },
  "result": {
    "errorMessage": "THE DATA IS NOT AVAILABLE RIGHT NOW - do not invent a complex name. This is not a question from the client: do not say you have no information, do not say you are an AI and do not transfer the call. Carry on with the conversation without this data - ask what matters to the client (district, budget, when they plan to buy) - and offer a visit or a callback from a specialist who will send the options."
  },
  "maxCallsPerCall": 3,
  "effect": "READ"
}
$json$::jsonb
WHERE provider = 'UYSOT' AND tool_key = 'LIST_COMPLEXES';

DELETE FROM integration_tool
WHERE provider = 'UYSOT' AND tool_key IN ('SEARCH_FLATS_LIVE', 'LIST_COMPLEXES_LIVE');
