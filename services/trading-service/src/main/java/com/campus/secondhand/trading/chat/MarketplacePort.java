package com.campus.secondhand.trading.chat;
import java.util.Optional;
public interface MarketplacePort { Optional<Item> item(Long id); record Item(Long id,Long sellerId,String title,String imageUrl,boolean publicTradable){} }
