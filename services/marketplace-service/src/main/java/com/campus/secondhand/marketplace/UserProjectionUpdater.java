package com.campus.secondhand.marketplace;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class UserProjectionUpdater {
    private final UserProjectionWriteTransaction writes;
    public UserProjectionUpdater(UserProjectionWriteTransaction writes){this.writes=writes;}
    public void accept(UserPublicProfileChanged event){
        for(int attempt=1;attempt<=3;attempt++){
            try{writes.apply(event);return;}
            catch(ConcurrencyFailureException|DataIntegrityViolationException error){if(attempt==3)throw error;Thread.onSpinWait();}
        }
    }
}
