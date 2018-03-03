#pragma once
#include "mce/textureptr.h"
class ItemInstance;
class BaseEntityRenderContext;
class EntityRenderData;
class ItemGraphics {
public:
	//char filler[16 /*sizeof(mce::TexturePtr)*/]; // 0
	mce::TexturePtr texturePtr;
	ItemGraphics(mce::TexturePtr&& _texturePtr) {
		texturePtr = std::move(_texturePtr);
	}
	ItemGraphics() {
	}
};
static_assert(sizeof(ItemGraphics) == 24, "itemGraphics size");
class ItemRenderer {
public:
	char filler[232];
	std::vector<ItemGraphics> itemGraphics; // 232
	void _loadItemGraphics();
	void* getAtlasPos(ItemInstance const&);
	void render(BaseEntityRenderContext&, EntityRenderData&);

	static mce::TexturePtr const& getGraphics(ItemInstance const&);
	static mce::TexturePtr const& getGraphics(Item const&);
};
static_assert(offsetof(ItemRenderer, itemGraphics) == 232, "itemrenderer offset wrong");