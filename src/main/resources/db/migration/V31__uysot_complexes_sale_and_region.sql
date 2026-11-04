-- listResidentialComplexes read a field Uysot does not send: HouseListDTO serialises its on-sale
-- flag as "sale" (v1/open-api/house/all), not "isSale". The mapping kept no flag at all, so the
-- model, told to offer only complexes with isSale=true, found none and said it did not know.
--
-- The open API has no region filter for complexes or flats (cityIds/districtIds exist only on
-- client/filter); a complex carries its location only in the free-text address. So "which
-- complexes do you have in Navoiy?" is answered from that address, which the description now says.

UPDATE integration_tool
SET definition = jsonb_set(jsonb_set(definition,
        '{responseMapping,complexes}',
        '"$.data[*]{id,name,sale,address,floor,release} || $.data.data[*]{id,name,sale,address,floor,release} || $.data.content[*]{id,name,sale,address,floor,release}"'),
        '{description}',
        '"Lists the company''s residential complexes with their address, floors and planned hand-over date. sale is true for a complex whose flats are on sale: offer only those. Call it when the client asks which projects there are, where they are or when they will be finished, and to get a complex''s id for searchFlats. When the client asks about a city or region (e.g. Navoiy, Samarqand), answer from each complex''s address: name the ones located there, and if none is, say so honestly and name the ones on sale elsewhere."'),
    updated_at = now()
WHERE provider = 'UYSOT' AND tool_key = 'LIST_COMPLEXES';
