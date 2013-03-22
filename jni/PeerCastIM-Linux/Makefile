CPPFLAGS = $(XCPPFLAGS) -DENABLE_BINRELOC -pthread  -D__cplusplus -D_UNIX -D_REENTRANT $(INCLUDES) 
LDFLAGS = -pthread 
LDSOFLAGS = -shared -Wl,-soname

CPPFLAGS += -g
#CPPFLAGS = -static -pthread -D__cplusplus -D_UNIX -D_LINUX_STATIC -D_REENTRANT $(INCLUDES)
#LDFLAGS = -static -pthread 

LD = g++
CC = gcc
AR = ar

INCLUDES = -Icore -Icore/common 

TARGET = peercast
CORE = core
SRCHTML = ui/html
HTML = html

CORESOURCE = $(CORE)/unix/usys.cpp \
	 $(CORE)/unix/usocket.cpp \
	 $(CORE)/common/socket.cpp \
	 $(CORE)/common/servent.cpp \
	 $(CORE)/common/servhs.cpp \
	 $(CORE)/common/servmgr.cpp \
	 $(CORE)/common/xml.cpp \
	 $(CORE)/common/stream.cpp \
	 $(CORE)/common/sys.cpp \
	 $(CORE)/common/gnutella.cpp \
	 $(CORE)/common/html.cpp \
	 $(CORE)/common/channel.cpp \
	 $(CORE)/common/http.cpp \
	 $(CORE)/common/inifile.cpp \
	 $(CORE)/common/peercast.cpp \
	 $(CORE)/common/stats.cpp \
	 $(CORE)/common/mms.cpp \
	 $(CORE)/common/mp3.cpp \
	 $(CORE)/common/nsv.cpp \
	 $(CORE)/common/ogg.cpp \
	 $(CORE)/common/url.cpp \
	 $(CORE)/common/icy.cpp \
	 $(CORE)/common/pcp.cpp \
	 $(CORE)/common/jis.cpp 



COREOBJ = $(CORESOURCE:.cpp=.o)

COREOBJSTATIC = $(CORESOURCE:.cpp=.a)

LINUXSOURCE = ui/linux/main.cpp 
LINUXOBJ = $(LINUXSOURCE:.cpp=.o)

# Here follow the generic build rules.
all:  $(TARGET)
	

clean:
	rm  -f $(LINUXOBJ) $(COREOBJ) $(TARGET) ${LIBOBJSTATIC} ${LIBOBJSHARED} $(COREOBJSTATIC)

%.o: %.cpp
	$(CC) $(INCLUDES) $(CPPFLAGS) -fPIC -c $< -o $@

%.a: %.cpp
	$(CC) $(INCLUDES) $(CPPFLAGS) -c $< -o $@

#DEPENDFILE = makedepend


depend: 
	echo > $(DEPENDFILE)
	$(CC) $(INCLUDES) $(CPPFLAGS) -MD $(CORESOURCE) $(LINUXSOURCE) >> $(DEPENDFILE)



$(TARGET): $(LINUXOBJ) $(COREOBJ)
	$(LD) $(LDFLAGS) -o $@ $(LINUXOBJ) $(COREOBJ) $(LIBS)
	

#include $(DEPENDFILE)
