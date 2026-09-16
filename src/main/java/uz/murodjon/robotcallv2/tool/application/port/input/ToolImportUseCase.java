package uz.murodjon.robotcallv2.tool.application.port.input;

import uz.murodjon.robotcallv2.tool.application.dto.ImportToolsRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ImportToolsResponse;
import uz.murodjon.robotcallv2.tool.application.dto.OpenApiOperationRow;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.List;

/** Tools from an OpenAPI 3 document (UNIVERSAL-OPERATOR-PLAN B.2). */
public interface ToolImportUseCase {

    /** Every operation of the document and whether it can become a tool. Creates nothing. */
    List<OpenApiOperationRow> findOperations(ImportToolsRequest request);

    /**
     * The tools the chosen operations would become, built but not saved — what the pack
     * generator puts in a draft for a person to look at before anything exists
     * (UNIVERSAL-OPERATOR-PLAN E.1).
     *
     * <p>Unsaved on purpose: a draft that created rows would leave a company full of tools
     * belonging to packs nobody installed. With no {@code operationIds} it returns every
     * operation the document supports, which is what a generator asks for when it is the model
     * that decides which ones the agent needs.
     */
    List<Tool> findTools(ImportToolsRequest request);

    /** One tool per chosen operation, all or none. The secrets they read are named, not created. */
    ImportToolsResponse importTools(long companyId, ImportToolsRequest request);
}
