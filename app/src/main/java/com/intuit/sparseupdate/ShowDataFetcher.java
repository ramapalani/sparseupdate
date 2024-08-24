package com.intuit.sparseupdate;

import com.intuit.sparseupdate.generated.DgsConstants;
import com.intuit.sparseupdate.generated.types.CreateShowInput;
import com.intuit.sparseupdate.generated.types.Show;
import com.intuit.sparseupdate.generated.types.UpdateShowInput;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import graphql.schema.DataFetchingEnvironment;

import java.util.*;

@DgsComponent
public class ShowDataFetcher {

//    private static final Logger LOGGER = LoggerFactory.getLogger(ShowDataFetcher.class);
    private final Map<String,Show> data = new HashMap<>();

    public ShowDataFetcher() {
        Show one = createOrUpdateShow("one", "Show One", 2023, "some default value");
        data.put(one.getId(), one);
    }

    @DgsQuery
    public Show show(@InputArgument String id) {
        return data.get(id);
    }

    @DgsMutation
    public Show createOrUpdateShow(@InputArgument CreateShowInput input) {
        // createShowInput should be validated for correct values
        // but there is no need to check whether or not a field input is provided by the API caller.
        Show show = createOrUpdateShow(input.getTitle(),
                input.getReleaseYear(),
                input.getFieldWithDefaultValue());
        data.put(show.getId(), show);
        return show;
    }

    @DgsMutation(field = DgsConstants.MUTATION.UpdateShow)
    public Show updateShowSimpleCheck(@InputArgument UpdateShowInput input,  DataFetchingEnvironment dfe) {
        // When a value is not provided by an API Caller, the object got through @InputArgument will null value for all nullable fields
        // We wouldn't be able to determine whether user provided the null value or graphql-java coerced it to null
        // To determine whether the API caller provided a certain input use DataFetchingEnvironment
        Map<String,Object> rawArgumentsMap = dfe.getExecutionStepInfo().getArgument(
                DgsConstants.MUTATION.UPDATESHOW_INPUT_ARGUMENT.Input);

        // Even when you are supporting sparse update, it is better to get an object through
        // @InputArgument, as it will convert/box all values to appropriate Java types

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Use DataFetchingEnvironment to determine what all fields are provided by the API Caller
        // Use @InputArgument to get values of the input, as DGS/GraphQL Java will convert it to appropriate Java Type
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // DgsConstants.UPDATESHOWINPUT.Id is a required field,
        // so if the execution comes to this point user has provided it.
        // so no need to check whether API caller provided any "required/Non-nullable field"
        Show beforeUpdate = data.get(input.getId());
        if (beforeUpdate == null) {
            String msg = String.format("Show with id %s not found", input.getId());
            throw new DgsEntityNotFoundException(msg);
        }

        return sparseUpdateMapper(beforeUpdate, input, rawArgumentsMap);
    }

    private Show sparseUpdateMapper(Show beforeUpdate, UpdateShowInput apiCallerInput, Map<String,Object>rawMap) {
        //////////////////////////////////////////////////////////
        // Non-Null Fields
        //////////////////////////////////////////////////////////
        // DgsConstants.UPDATESHOWINPUT.Id (id)
        // This will always be provided by the API caller, no need to check the presence
        String id = apiCallerInput.getId();

        //////////////////////////////////////////////////////////
        // Nullable field but with defaults in GraphQL Schema
        //////////////////////////////////////////////////////////
        // DgsConstants.UPDATESHOWINPUT.FieldWithDefaultValue (fieldWithDefaultValue)
        // These fields will always have value at this point of execution
        // So always set this.
        // If you want this to be updated only when provided by the API caller
        // Remove default value from GraphQL schema (make it a nullable non-default field)
        String fieldWithDefaultValue = apiCallerInput.getFieldWithDefaultValue();

        //////////////////////////////////////////////////////////
        // Nullable non-default fields
        // Primary set of fields that are used for sparse update
        //////////////////////////////////////////////////////////
        String title = beforeUpdate.getTitle();
        boolean isTitlePresent = rawMap.containsKey(DgsConstants.UPDATESHOWINPUT.Title);
        if (isTitlePresent) {
            title = apiCallerInput.getTitle();
        }
        Integer releaseYear = beforeUpdate.getReleaseYear();
        boolean isReleaseYearPresent = rawMap.containsKey(DgsConstants.UPDATESHOWINPUT.ReleaseYear);
        if (isReleaseYearPresent) {
            releaseYear = apiCallerInput.getReleaseYear();
        }

        Show sparseUpdatedShow = createOrUpdateShow(id, title, releaseYear, fieldWithDefaultValue);
        return sparseUpdatedShow;
    }

    private Show createOrUpdateShow(String title, Integer releaseYear, String fieldWithDefaultValue) {
        return createOrUpdateShow(null, title, releaseYear, fieldWithDefaultValue);
    }
    private Show createOrUpdateShow(String id, String title, Integer releaseYear, String fieldWithDefaultValue) {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        return Show.newBuilder()
                .id(id)
                .title(title)
                .releaseYear(releaseYear)
                .fieldWithDefaultValue(fieldWithDefaultValue)
                .build();
    }
}