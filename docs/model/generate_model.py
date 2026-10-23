from pathlib import Path
from html import escape
from PIL import Image, ImageDraw, ImageFont
p=Path(__file__).resolve().parent
W,H=2140,1550
im=Image.new('RGB',(W,H),'white');d=ImageDraw.Draw(im)
font='/System/Library/Fonts/Supplemental/Arial.ttf'
f=ImageFont.truetype(font,21); small=ImageFont.truetype(font,19); title=ImageFont.truetype(font,32)
s=['<svg xmlns="http://www.w3.org/2000/svg" width="2140" height="1550" viewBox="0 0 2140 1550"><rect width="2140" height="1550" fill="white"/>']
def line(points,dash=False):
 if dash:
  for (x,y),(a,b) in zip(points,points[1:]):
   length=((a-x)**2+(b-y)**2)**.5
   for k in range(0,int(length),13):
    e=min(k+7,length); d.line((x+(a-x)*k/length,y+(b-y)*k/length,x+(a-x)*e/length,y+(b-y)*e/length),fill='#52616b',width=2)
 else:d.line(points,fill='#25353c',width=2)
 s.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in points)+'" fill="none" stroke="#52616b" stroke-width="2"'+(' stroke-dasharray="7 6"' if dash else '')+'/>')
def text(x,y,t,size=21,color='#172f38'):
 ff=title if size==32 else small if size==19 else f
 d.text((x,y),t,font=ff,fill=color)
 s.append(f'<text x="{x}" y="{y+size}" font-family="Arial,sans-serif" font-size="{size}" fill="{color}">{escape(t)}</text>')
def rect(x,y,w,h):
 d.rectangle((x,y,x+w,y+h),fill='white',outline='#25353c',width=2)
 s.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="white" stroke="#25353c" stroke-width="2"/>')
def circle(x,y):
 d.ellipse((x-5,y-5,x+5,y+5),fill='#25353c');s.append(f'<circle cx="{x}" cy="{y}" r="5" fill="#25353c"/>')
def diamond(x,y):
 pts=[(x,y-7),(x+7,y),(x,y+7),(x-7,y)]
 d.polygon(pts,fill='white',outline='#25353c',width=2);s.append('<polygon points="'+' '.join(f'{a},{b}' for a,b in pts)+'" fill="white" stroke="#25353c" stroke-width="2"/>')
def edge(pts,opt=False):
 line(pts,True)
 if opt:diamond(*pts[0])
 circle(*pts[-1])
def box(x,y,w,name,ru,attrs):
 text(x,y-57,ru);text(x,y-29,name,size=19)
 h=58+len(attrs)*29
 rect(x,y,w,h);text(x+14,y+13,'id_'+name+'  (PK)')
 line([(x,y+51),(x+w,y+51)])
 for i,a in enumerate(attrs):text(x+14,y+60+i*29,a,size=19)
 return h
text(60,25,'Медицинская лаборатория · Инфологическая модель IDEF1X',32)
text(60,75,'Проект схемы · 10 сущностей · склад выдаёт → подразделение принимает · все связи 1:N',21)
box(60,170,360,'units','Единицы измерения',['name','symbol'])
box(540,170,390,'materials','Материалы',['units_id (FK)','categories_id (FK)','code','name','has_expiry','is_active'])
box(1050,170,390,'material_batches','Партии материалов',['materials_id (FK)','batch_number','expires_on (?)','purchase_unit_price (?)'])
box(1570,170,510,'inventory_document_items','Строки документов',['inventory_documents_id (FK)','material_batches_id (FK)','line_number','quantity'])
box(60,520,360,'categories','Категории материалов',['name'])
box(1570,510,510,'inventory_documents','Документы учёта',['warehouses_id (FK)','suppliers_id (FK, ?)','departments_id (FK, ?)','users_id (FK)','number','type, status','document_date','posted_at (?)','write_off_reason (?)'])
box(60,950,360,'departments','Подразделения — принимают',['departments_id (FK, ?)','name','is_active'])
box(540,950,390,'users','Пользователи',['departments_id (FK, ?)','login','password_hash','full_name','role','is_active'])
box(1050,950,390,'warehouses','Склады — выдают',['name','is_active'])
box(1570,950,510,'suppliers','Поставщики',['name','inn (?)','contact_info (?)','is_active'])
edge([(420,210),(540,210)])
edge([(420,570),(475,570),(475,425),(700,425),(700,402)])
edge([(930,250),(1050,250)])
edge([(1440,250),(1570,250)])
edge([(1810,510),(1810,344)])
edge([(420,1010),(540,1010)],True)
edge([(420,980),(445,980),(445,690),(1570,690)],True)
edge([(930,1040),(980,1040),(980,770),(1570,770)])
edge([(1440,1000),(1490,1000),(1490,620),(1570,620)])
edge([(1830,950),(1830,829)],True)
edge([(60,1030),(25,1030),(25,1170),(180,1170),(180,1095)],True)
text(65,1195,'Иерархия departments: departments_id → id_departments',19)
line([(60,1275),(2080,1275)])
text(60,1298,'Обозначения',21)
line([(60,1350),(230,1350)],True);circle(230,1350)
text(250,1336,'Связи 1:N, неидентифицирующие; точка у потомка: 0..N записей',19)
line([(60,1393),(230,1393)],True);diamond(60,1393);circle(230,1393)
text(250,1379,'Ромб у родителя: ссылка необязательна (0..1); без ромба — ровно 1 родитель',19)
text(60,1435,'PK — первичный ключ; FK — внешний ключ; ? — допускается отсутствие значения',19)
text(60,1475,'Все PK самостоятельные: внешние ключи не входят в PK. Условия по типам документов приведены в описании модели.',19)
s.append('</svg>');(p/'laboratory-idef1x.svg').write_text('\n'.join(s));im.save(p/'laboratory-idef1x.png')
