/*
 * Copyright (c) 2008, 2009, 2010 Denis Tulskiy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with this work.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tulskiy.musique.gui.playlist;

import com.tulskiy.musique.audio.player.Player;
import com.tulskiy.musique.audio.player.PlayerEvent;
import com.tulskiy.musique.audio.player.PlayerListener;
import com.tulskiy.musique.db.DBMapper;
import com.tulskiy.musique.playlist.Playlist;
import com.tulskiy.musique.playlist.PlaylistManager;
import com.tulskiy.musique.playlist.Song;
import com.tulskiy.musique.system.Application;
import com.tulskiy.musique.system.Configuration;
import com.tulskiy.musique.system.PluginLoader;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Vector;

/**
 * @Author: Denis Tulskiy
 * @Date: Feb 6, 2010
 */
public class PlaylistPanel extends JPanel {
    private DBMapper<PlaylistColumn> columnDBMapper = DBMapper.create(PlaylistColumn.class);
    private DBMapper<Song> songDBMapper = DBMapper.create(Song.class);

    private Application app = Application.getInstance();
    private Configuration config = app.getConfiguration();
    private PlaylistTable table;
    private PlaylistManager playlistManager;
    private JComboBox playlistSelection;
    private ArrayList<PlaylistColumn> columns = new ArrayList<PlaylistColumn>();
    private Playlist playlist;
    private JTextField searchField;

    //stuff for popup menu
    private TableColumn tc;
    private Song song;
    private JFrame parentFrame;

    public PlaylistPanel() {
        playlistManager = app.getPlaylistManager();
        playlist = playlistManager.getCurrentPlaylist();
        playlistSelection = new JComboBox(new Vector<Playlist>(playlistManager.getPlaylists()));
        playlistSelection.setSelectedItem(playlist);

        playlistSelection.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();
                playlist = (Playlist) box.getSelectedItem();
                searchField.setText("");
                table.setPlaylist(playlist);
                playlistManager.selectPlaylist(playlist);
            }
        });

        columnDBMapper.loadAll("select * from playlist_columns order by position", columns);
        table = new PlaylistTable(playlist, columns);
        app.getPlayer().setPlaybackOrder(table);

        setLayout(new BorderLayout());
        Box box = new Box(BoxLayout.X_AXIS);
        box.add(Box.createHorizontalStrut(5));
        box.add(new JLabel("Playlist "));
        box.add(playlistSelection);
        box.add(Box.createHorizontalStrut(10));
        box.add(new JLabel("Search: "));
        searchField = new JTextField();
        searchField.setMaximumSize(new Dimension(300, 40));
        searchField.setPreferredSize(new Dimension(300, 0));
        box.add(searchField);
        box.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        add(box, BorderLayout.NORTH);

        JScrollPane tableScrollPane = new JScrollPane(table);
        add(tableScrollPane, BorderLayout.CENTER);

        int lastPlayed = config.getInt("player.lastPlayed", -1);
        table.setLastPlayed(new Song(lastPlayed));

        buildListeners();
        createPopupMenu();
    }

    private void createPopupMenu() {
        final JPopupMenu headerMenu = new JPopupMenu();
        final JTableHeader header = table.getTableHeader();

        headerMenu.add(new JMenuItem("Add Column")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlaylistColumn column = new PlaylistColumn();
                ColumnDialog dialog = new ColumnDialog(getParentFrame(), "Add Column", column);
                if (dialog.showDialog()) {
                    table.saveColumns();
                    columns.add(column);
                    table.createDefaultColumnsFromModel();
                    columnDBMapper.save(column);
                }
            }
        });
        headerMenu.add(new JMenuItem("Edit Column")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tc == null) return;
                PlaylistColumn column = columns.get(tc.getModelIndex());
                ColumnDialog dialog = new ColumnDialog(getParentFrame(), "Edit Column", column);
                if (dialog.showDialog()) {
                    tc.setHeaderValue(column.getName());
                    table.update();
                }
            }
        });
        headerMenu.add(new JMenuItem("Remove Column")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tc == null) return;
                PlaylistColumn pc = columns.remove(tc.getModelIndex());
                table.createDefaultColumnsFromModel();
                columnDBMapper.delete(pc);
            }
        });

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                show(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                show(e);
            }

            public void show(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = header.getColumnModel().getColumnIndexAtX(e.getX());
                    if (index != -1) {
                        tc = header.getColumnModel().getColumn(index);
                    }
                    headerMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        final JPopupMenu tableMenu = new JPopupMenu();

        tableMenu.add(new JMenuItem("Add to Queue")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });

        tableMenu.add(new JMenuItem("Remove")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.removeSelected();
            }
        });

        tableMenu.add(new JMenuItem("Properties")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showInfo(song);
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                show(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                show(e);
            }

            public void show(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    song = table.selectSongAt(e.getPoint());
                    tableMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

    }

    public JFrame getParentFrame() {
        if (parentFrame == null) {
            parentFrame = (JFrame) getRootPane().getParent();
        }
        return parentFrame;
    }

    public void showInfo(Song s) {
        SongInfoDialog dialog = new SongInfoDialog(getParentFrame(), s);
        if (dialog.showDialog()) {
            try {
                songDBMapper.save(song);
                PluginLoader.getAudioFileWriter(song.getFilePath()).write(song);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void buildListeners() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    app.getPlayer().open(table.getSelectedSong());
                    app.getPlayer().play();
                }
            }
        });

        final Player player = app.getPlayer();
        player.addListener(new PlayerListener() {
            public void onEvent(PlayerEvent e) {
                table.update();
                switch (e.getEventCode()) {
                    case FILE_OPENED:
                        table.scrollToSong(player.getSong());
                }
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                table.filter(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                table.filter(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                table.filter(searchField.getText());
            }
        });
    }

    public void shutdown() {
        table.saveColumns();

        for (PlaylistColumn c : columns) {
            columnDBMapper.save(c);
        }
    }

    public void addPlaylist(String name) {
        playlist = playlistManager.addPlaylist(name);
        playlistSelection.addItem(playlist);
        playlistSelection.setSelectedItem(playlist);
    }

    private JMenuItem newItem(String name, String hotkey, ActionListener al) {
        JMenuItem item = new JMenuItem(name);
        item.setAccelerator(KeyStroke.getKeyStroke(hotkey));
        item.addActionListener(al);

        return item;
    }

    public void addMenu(JMenuBar menuBar) {
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenu editMenu = new JMenu("Edit");
        menuBar.add(editMenu);
        JMenu viewMenu = new JMenu("View");
        menuBar.add(viewMenu);
        JMenu playbackMenu = new JMenu("Playback");
        menuBar.add(playbackMenu);

        fileMenu.add("New Playlist").addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String name = JOptionPane.showInputDialog("Enter Playlist Name");
                addPlaylist(name);
            }
        });

        fileMenu.add("Remove Playlist").addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playlistManager.removePlaylist(playlist);
                playlistSelection.removeItem(playlist);
                if (playlistManager.getTotalPlaylists() == 0) {
                    addPlaylist("Default");
                }
                playlistSelection.setSelectedIndex(0);
            }
        });

        fileMenu.addSeparator();

        fileMenu.add("Add Files").addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser();
                fc.setMultiSelectionEnabled(true);
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                int retVal = fc.showOpenDialog(null);
                if (retVal == JFileChooser.APPROVE_OPTION) {
//                    ProgressMonitor pm = new ProgressMonitor(null, "Adding Files", "", 0, 100);
                    playlistManager.getCurrentPlaylist().addFiles(fc.getSelectedFiles());
                }

                table.update();
            }
        });

        fileMenu.addSeparator();

        fileMenu.add(newItem("Quit", "control Q", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                app.exit();
            }
        }));


        editMenu.add("Clear").addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Playlist pl = playlistManager.getCurrentPlaylist();
                pl.clear();
                table.update();
            }
        });

        editMenu.add(newItem("Remove", "DELETE", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.removeSelected();
            }
        }));


        JMenu laf = new JMenu("Look and Feel");
        ActionListener lafListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String cmd = e.getActionCommand();
                    if (cmd.equals("Metal")) {
                        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                    } else if (cmd.equals("Nimbus")) {
                        UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
                    } else {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    }
                    shutdown();
                    app.start();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        };

        laf.add("Metal").addActionListener(lafListener);
        laf.add("Nimbus").addActionListener(lafListener);
        laf.add("Native").addActionListener(lafListener);
        viewMenu.add(laf);

        JMenu orderMenu = new JMenu("Order");
        playbackMenu.add(orderMenu);
        ActionListener orderListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem item = (JMenuItem) e.getSource();
                PlaylistTable.Order o = PlaylistTable.Order.valueOf(item.getName());
                table.setOrder(o);
                config.setInt("player.playbackOrder", o.ordinal());
            }
        };

        int index = config.getInt("player.playbackOrder", 0);
        PlaylistTable.Order[] orders = PlaylistTable.Order.values();
        ButtonGroup gr = new ButtonGroup();
        for (PlaylistTable.Order o : orders) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(o.getText());
            if (o.ordinal() == index)
                item.setSelected(true);
            item.addActionListener(orderListener);
            item.setName(o.toString());
            gr.add(item);
            orderMenu.add(item);
        }
    }
}
