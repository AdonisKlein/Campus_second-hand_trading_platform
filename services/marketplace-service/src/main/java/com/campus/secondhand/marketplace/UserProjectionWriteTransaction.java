package com.campus.secondhand.marketplace;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserProjectionWriteTransaction {
    private final SearchableUserProjectionRepository projections;
    UserProjectionWriteTransaction(SearchableUserProjectionRepository projections){this.projections=projections;}
    @Transactional
    void apply(UserPublicProfileChanged event){
        SearchableUserProjection projection=projections.findById(event.userId()).orElseGet(SearchableUserProjection::new);
        projection.apply(event);
        projections.saveAndFlush(projection);
    }
}
