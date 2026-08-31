package com.campus.secondhand.trading.chat;
import java.util.Optional;
public interface AccountPort { Optional<Account> activeStudent(Long id); record Account(Long id,String nickname,String username){} }
