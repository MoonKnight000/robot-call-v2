package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;

import java.util.List;
import java.util.Map;

/**
 * One request in a chained target source.
 *
 * <p>The first step <em>lists</em>: it is called once (or once per page) and its answer is
 * an array of rows. Every step after it <em>enriches</em>: it is called once per surviving
 * row, and what it extracts is added to that row's variables. That is the shape a real CRM
 * forces — the endpoint that knows who is overdue is rarely the endpoint that knows their
 * phone number — and expressing it as configuration is what lets a company point this at
 * their own API from the campaign screen instead of asking for code.
 *
 * <p>Anywhere below that takes {@code {{name}}} is filled from the variables collected so
 * far, so a later step's URL can read {@code .../contract/{{contractId}}}. Substituted
 * values are URL-encoded in a URL and JSON-escaped in a body. The list step additionally
 * has {@code {{page}}} when {@link #paginate} is set.
 *
 * @param name      what this step is called, for the error a rejected row reports
 * @param itemsPath dot path to the array of rows in this step's answer
 *                  ({@code "data.data"}); only the list step uses it, and null there means
 *                  the body is itself the array
 * @param paginate  list step only: call it again with {@code {{page}}} incremented until a
 *                  page comes back short or the row cap is reached. Without it the step is
 *                  called exactly once and whatever it answered is the whole list
 * @param filters   list step only: a row is carried on only if it satisfies every one of
 *                  them. This is what keeps the chain from spending two requests each on
 *                  clients who owe nothing
 * @param extract   variable name → dot path in this step's answer. On the list step the
 *                  paths are relative to each row; on a later step, to the whole response
 *                  body. Whichever variables the source names as its phone, client id and
 *                  language are used as those; every other one becomes a fact
 */
public record TargetSourceStep(
        String name,
        TargetSourceMethod method,
        String url,
        String body,
        String itemsPath,
        boolean paginate,
        List<TargetSourceStepFilter> filters,
        Map<String, String> extract
) {

    public TargetSourceStep {
        method = method != null ? method : TargetSourceMethod.GET;
        filters = filters == null ? List.of() : List.copyOf(filters);
        extract = extract == null ? Map.of() : Map.copyOf(extract);
    }
}
